/*
 * Copyright 2021 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;

/**
 * Creates aliases for columns and tables.
 *
 * @author Jens Schauder
 * @since 2.4
 */
class AliasFactory {

	private final PathAnalyzer pathAnalyzer;
	private final Map<RelationalPersistentEntity<?>, Integer> nextColumnIndex = new HashMap<>();
	private final Map<RelationalPersistentEntity<?>, Integer> tableIndexes = new HashMap<>();

	private int tableIndex = 0;
	private int viewIndex = 0;

	AliasFactory(PathAnalyzer pathAnalyzer) {
		this.pathAnalyzer = pathAnalyzer;
	}

	String getAlias(RelationalPersistentEntity<?> entity) {

		Integer index = tableIndexes.computeIfAbsent(entity, e -> tableIndex++);
		return "T" + index + "_" + sanitizedName(entity);
	}

	String getAlias(PersistentPropertyPath<RelationalPersistentProperty> path) {

		RelationalPersistentProperty property = path.getRequiredLeafProperty();
		final String suffix = property.getName();
		return getAlias(path, suffix);
	}

	String getAlias(PersistentPropertyPath<RelationalPersistentProperty> path, String suffix) {
		return getPrefix(path) + "_" + sanitize(suffix);
	}

	private String getPrefix(PersistentPropertyPath<RelationalPersistentProperty> path) {

		return "T" + tableIndexes.get(pathAnalyzer.getTableOwner(path)) + "_C"
				+ nextColumnIndex.compute(pathAnalyzer.getTableOwner(path), (e, i) -> i == null ? 0 : ++i);
	}

	private String sanitizedName(RelationalPersistentEntity<?> entity) {
		return sanitize(entity.getName());
	}

	private String sanitize(String name) {

		int dotIndex = name.lastIndexOf(".");
		int dollarIndex = name.lastIndexOf("$");

		// only use the last part if this is a full qualified class name
		String className = name.substring(Math.max(dotIndex, dollarIndex) + 1);

		String result = className.toUpperCase().replaceAll("[^\\x41-\\x5A\\x30-\\x39]", "");
		return result;
	}

	String getViewAlias() {
		return "V" + viewIndex++;
	}
}
