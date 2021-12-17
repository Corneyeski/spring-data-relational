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

import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

class PersistentPathUtil {
	private final RelationalMappingContext context;
	private final RelationalPersistentEntity<?> root;

	PersistentPathUtil(RelationalMappingContext context, RelationalPersistentEntity<?> root) {
		this.context = context;
		this.root = root;
	}

	PersistentPropertyPath<RelationalPersistentProperty> extend(
			PersistentPropertyPath<RelationalPersistentProperty> basePath, RelationalPersistentProperty property) {

		if (basePath.isEmpty()) {
			return path(property);
		} else {
			return context.getPersistentPropertyPath(basePath.toDotPath() + "." + property.getName(),
					basePath.getBaseProperty().getOwner().getType());
		}
	}

	protected PersistentPropertyPath<RelationalPersistentProperty> path(RelationalPersistentProperty property) {
		return context.getPersistentPropertyPath(property.getName(), root.getType());
	}
}
