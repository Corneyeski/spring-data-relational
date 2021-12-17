/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jdbc.core.convert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mapping.Parameter;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.lang.Nullable;

class AggregateResultSetExtractor<T> implements org.springframework.jdbc.core.ResultSetExtractor<Iterable<T>> {

	private static final Log log = LogFactory.getLog(AggregateResultSetExtractor.class);

	private final RelationalMappingContext context;
	private final RelationalPersistentEntity<T> rootEntity;
	private final JdbcConverter converter;
	private final Function<PersistentPropertyPath<RelationalPersistentProperty>, String> propertyToColumn;
	private final PersistentPathUtil persistentPathUtil;

	AggregateResultSetExtractor(RelationalMappingContext context, RelationalPersistentEntity<T> rootEntity,
			JdbcConverter converter,
			Function<PersistentPropertyPath<RelationalPersistentProperty>, String> propertyToColumn) {

		this.context = context;
		this.rootEntity = rootEntity;
		this.converter = converter;
		this.propertyToColumn = propertyToColumn;
		this.persistentPathUtil = new PersistentPathUtil(context, rootEntity);
	}

	@Override
	public Iterable<T> extractData(ResultSet resultSet) throws SQLException, DataAccessException {

		CachingResultSet crs = new CachingResultSet(resultSet);

		EntityInstantiator instantiator = converter.getEntityInstantiators().getInstantiatorFor(rootEntity);

		List<T> result = new ArrayList<>();

		ResultSetParameterValueProvider valueProvider = new ResultSetParameterValueProvider(crs,
				context.getPersistentPropertyPath("", rootEntity.getType()));

		String idColumn = propertyToColumn.apply(persistentPathUtil.path(rootEntity.getRequiredIdProperty()));

		Object oldId = null;
		while (crs.next()) {
			if (oldId == null) {
				oldId = crs.getObject(idColumn);
			}
			valueProvider.readValues();
			Object peekedId = crs.peek(idColumn);
			if (peekedId == null || peekedId != oldId) {

				Object instance = dehydrateInstance(instantiator, valueProvider, rootEntity);
				result.add((T) instance);
				oldId = peekedId;
			}
		}

		return result;
	}

	/**
	 * create an instance and populate all its properties
	 */
	private Object dehydrateInstance(EntityInstantiator instantiator, ResultSetParameterValueProvider valueProvider,
			RelationalPersistentEntity<?> entity) {

		Object instance = instantiator.createInstance(entity, valueProvider);

		PersistentPropertyAccessor<?> accessor = entity.getPropertyAccessor(instance);

		if (entity.requiresPropertyPopulation()) {
			entity.doWithProperties((PropertyHandler<RelationalPersistentProperty>) p -> {
				if (!entity.isCreatorArgument(p)) {
					accessor.setProperty(p, valueProvider.getValue(p));
				}
			});
		}
		return instance;
	}

	private class ResultSetParameterValueProvider implements ParameterValueProvider<RelationalPersistentProperty> {

		private final CachingResultSet rs;
		private Collection<Object> underConstruction = null;
		/**
		 * The path which is used to determine columnNames
		 */
		private final PersistentPropertyPath<RelationalPersistentProperty> basePath;
		private Map<RelationalPersistentProperty, Object> aggregatedValues = new HashMap<>();

		ResultSetParameterValueProvider(CachingResultSet rs,
				PersistentPropertyPath<RelationalPersistentProperty> basePath) {

			this.rs = rs;
			this.basePath = basePath;
		}

		@SuppressWarnings("unchecked")
		@Override
		@Nullable
		public <S> S getParameterValue(Parameter<S, RelationalPersistentProperty> parameter) {

			return (S) getValue(
					AggregateResultSetExtractor.this.rootEntity.getRequiredPersistentProperty(parameter.getName()));
		}

		@Nullable
		public Object getValue(RelationalPersistentProperty property) {

			String columnName = getColumnName(property);

			try {

				if (property.isCollectionLike()) {

					return aggregatedValues.get(property);

				}

				if (property.isEntity()) {

					if (rs.getObject(columnName) == null) { // there is a special column for determining if the is no entity vs,
																									// there is an entity but all columns are null.
						return null;
					}

					return readInstance(property);
				}

				return rs.getObject(columnName);
			} catch (SQLException e) {

				if (log.isDebugEnabled()) {
					log.debug("Failed to access column '" + columnName + "': " + e.getMessage());
				}

				return null;
			}
		}

		/**
		 * Read a single instance for the given property.
		 *
		 * @param property
		 * @return
		 */
		private Object readInstance(RelationalPersistentProperty property) {

			RelationalPersistentEntity<?> innerEntity = context.getRequiredPersistentEntity(property.getActualType());
			PersistentPropertyPath<RelationalPersistentProperty> extendedPath = persistentPathUtil.extend(basePath, property);
			ResultSetParameterValueProvider valueProvider = new ResultSetParameterValueProvider(rs, extendedPath);
			EntityInstantiator instantiator = converter.getEntityInstantiators().getInstantiatorFor(innerEntity);
			return dehydrateInstance(instantiator, valueProvider, innerEntity);
		}

		/**
		 * converts a property into a column name representing that property.
		 *
		 * @param property
		 * @return
		 */
		private String getColumnName(RelationalPersistentProperty property) {

			return propertyToColumn.apply(persistentPathUtil.extend(basePath, property));
		}

		/**
		 * read values for all collection like properties and aggregate them in a collection.
		 */
		void readValues() {

			if (basePath.isEmpty()) {
				rootEntity.forEach(p -> {
					if (p.isCollectionLike()) {
						Object object = null;
						try {
							object = rs.getObject(getColumnName(p));
						} catch (SQLException e) {
							e.printStackTrace();
						}
						if (object != null) {

							final Set<Object> set = (Set<Object>) aggregatedValues.computeIfAbsent(p, k -> new HashSet<>());
							set.add(readInstance(p));
						}
					}
				});
			} else {
				throw new UnsupportedOperationException("nested stuff isn't implemented yet");
			}

		}
	}

}
