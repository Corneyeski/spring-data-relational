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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

public class PersistentPathUtilUnitTests {

	JdbcMappingContext context = new JdbcMappingContext();
	RelationalPersistentEntity<?> root = context.getRequiredPersistentEntity(Root.class);
	RelationalPersistentProperty nameProperty = root.getPersistentProperty("name");

	PersistentPathUtil pathUtil = new PersistentPathUtil(context, root);

	@Test
	void extendEmptyPath() {

		final PersistentPropertyPath<RelationalPersistentProperty> emptyPath = context.getPersistentPropertyPath("", Root.class);

		PersistentPropertyPath<RelationalPersistentProperty> newPath = pathUtil.extend(emptyPath, nameProperty);

		assertThat(newPath.toDotPath()).isEqualTo("name");
	}

	@Test
	void extendNonEmptyPath() {

		final PersistentPropertyPath<RelationalPersistentProperty> path = context.getPersistentPropertyPath("root", Root.class);

		PersistentPropertyPath<RelationalPersistentProperty> newPath = pathUtil.extend(path, nameProperty);

		assertThat(newPath.toDotPath()).isEqualTo("root.name");
	}

	private static class Root {
		String name;
		Root root;
	}

}
