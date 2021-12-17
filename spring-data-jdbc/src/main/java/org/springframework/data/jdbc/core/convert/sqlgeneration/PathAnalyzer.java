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
import org.springframework.data.relational.core.mapping.PersistentPropertyPathExtension;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.util.Assert;

class PathAnalyzer {

	private final MappingContext<? extends RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> mappingContext;
	private final RelationalPersistentEntity entity;

	PathAnalyzer(MappingContext<? extends RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> mappingContext, RelationalPersistentEntity entity) {

		this.mappingContext = mappingContext;
		this.entity = entity;
	}

	RelationalPersistentEntity<?> getTableOwner(PersistentPropertyPath<RelationalPersistentProperty> path) {

		PersistentPropertyPath<RelationalPersistentProperty> tableOwningPath = getTableOwningPath(path);

		if (tableOwningPath.isEmpty()) {
			return entity;
		}

		RelationalPersistentProperty leafProperty = tableOwningPath.getRequiredLeafProperty();
		if (leafProperty.isCollectionLike()) {

			Class<?> componentType = leafProperty.getComponentType();
			Assert.notNull(componentType,
					() -> "The component type of the collection-like property " + leafProperty + " should not be null");
			return mappingContext.getRequiredPersistentEntity(componentType);
		}
		return leafProperty.getOwner();
	}

	PersistentPropertyPath<RelationalPersistentProperty> getTableOwningPath(
			PersistentPropertyPath<RelationalPersistentProperty> path) {

		if (isEmbedded(path)) {
			PersistentPropertyPath<RelationalPersistentProperty> parentPath = path.getParentPath();
			if (isEmbedded(parentPath)) {

				return getTableOwningPath(parentPath);
			}
			return path;
		}
		return path;
	}

	boolean isEmbedded(PersistentPropertyPath<RelationalPersistentProperty> path) {
		if (path.isEmpty()) {
			return false;
		}
		return path.getRequiredLeafProperty().isEmbedded() || isEmbedded(path.getParentPath());
	}

	SqlIdentifier getReverseColumnName(PersistentPropertyPath<RelationalPersistentProperty> parentPath) {
		return new PersistentPropertyPathExtension(mappingContext, parentPath).getReverseColumnName();
	}

	boolean isId(PersistentPropertyPath<RelationalPersistentProperty> path) {
		final RelationalPersistentProperty property = path.getRequiredLeafProperty();
		return property.getOwner().isIdProperty(property);
	}
}
