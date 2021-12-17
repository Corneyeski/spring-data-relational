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
package org.springframework.data.jdbc.core.convert.sqlgeneration;

import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;

class SelectStructureBuilder {

	private final MappingContext<RelationalPersistentEntity<?>, RelationalPersistentProperty> context;

	SelectStructureBuilder(RelationalMappingContext context) {

		this.context = context;
	}

	SelectStructure of(Class<?> rootType) {

		RelationalPersistentEntity<?> entity = context.getRequiredPersistentEntity(rootType);
		return of(entity);
	}

	SelectStructure of(RelationalPersistentEntity<?> entity) {

		PathAnalyzer pathAnalyzer = new PathAnalyzer(context, entity);
		AliasFactory aliasFactory = new AliasFactory(pathAnalyzer);
		PathToRelationalMapper pathToRelationalMapper = new PathToRelationalMapper(entity, new AliasFactory(pathAnalyzer),
				pathAnalyzer);
		SelectStructure selectStructure = new SelectStructure(entity, aliasFactory.getAlias(entity));

		for (PersistentPropertyPath<RelationalPersistentProperty> path : context
				.findPersistentPropertyPaths(entity.getType(), e -> true)) {

			RelationalPersistentProperty property = path.getRequiredLeafProperty();

			if (property.isEmbedded()) {
				continue;
			}

			SelectStructure.TableStructure tableStructure = selectStructure.getAncesterTableFor(path);

			if (property.isCollectionLike()) {

				RelationalPersistentEntity<?> tableOwningEntity = context.getRequiredPersistentEntity(property.getActualType());
				SqlIdentifier alias = SqlIdentifier.unquoted(aliasFactory.getAlias(tableOwningEntity));

				// TODO: This gets interesting when the collection is owned by a one to one reference.
				SqlIdentifier viewAlias = SqlIdentifier.unquoted(aliasFactory.getViewAlias());
				SqlIdentifier backReference = pathToRelationalMapper.getTableName(path.getParentPath());
				SqlIdentifier backReferenceAlias = SqlIdentifier.unquoted(aliasFactory.getAlias(path, backReference.getReference()));
				SqlIdentifier rowNumberAlias = SqlIdentifier.unquoted(aliasFactory.getAlias(path, "RN"));
				tableStructure.addAnalyticJoin(path, viewAlias, tableOwningEntity, alias, backReference, backReferenceAlias, rowNumberAlias);

			} else if (property.isEntity()) {
				RelationalPersistentEntity<?> tableOwningEntity = context.getRequiredPersistentEntity(property.getActualType());
				SqlIdentifier alias = SqlIdentifier.unquoted(aliasFactory.getAlias(tableOwningEntity));
				tableStructure.addJoin(path, tableOwningEntity, alias);
			} else {

				SqlIdentifier columnIdentifier = pathToRelationalMapper.getColumnIdentifier(path);
				SqlIdentifier alias = SqlIdentifier.unquoted(aliasFactory.getAlias(path));
				if (pathAnalyzer.isId(path)) {
					tableStructure.addId(path, columnIdentifier, alias);
				}else {
					tableStructure.addColumn(path, columnIdentifier, alias);
				}
			}

		}

		return selectStructure;
	}

}
