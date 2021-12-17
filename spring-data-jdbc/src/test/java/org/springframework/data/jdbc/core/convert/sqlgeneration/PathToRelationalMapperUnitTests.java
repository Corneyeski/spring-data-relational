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

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.AnalyticFunction;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.relational.core.sql.Table;

/**
 * Tests the behavior of {@link PathToRelationalMapper}.
 *
 * @author Jens Schauder
 */
public class PathToRelationalMapperUnitTests {

	private PathToRelationalMapper pathToRelationalMapper;

	private JdbcMappingContext mappingContext = new JdbcMappingContext();

	{
		RelationalPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(DummyEntity.class);
		PathAnalyzer pathAnalyzer = new PathAnalyzer(mappingContext, entity);
		AliasFactory aliasFactory = new AliasFactory(pathAnalyzer);
		pathToRelationalMapper = new PathToRelationalMapper(entity, aliasFactory, pathAnalyzer);
	}

	@Nested
	class SimpleProperties {

		@Test
		void getTableForSimpleProperty() {

			Table table = pathToRelationalMapper.getQueryMappingInfo(path("string")).table;

			assertThat(table).isEqualTo(tableAs("DUMMY_ENTITY", "T0_DUMMYENTITY"));
		}

		@Test
		void getColumnForSimpleProperty() {

			Expression column = pathToRelationalMapper.getExpression(path("string"));

			assertThat(column).isEqualTo(column(tableAs("DUMMY_ENTITY", "T0_DUMMYENTITY"), "STRING", "T0_C0_STRING"));
		}
	}

	@Nested
	class Entities {
		@Test
		void getTableForEntity() {

			Table table = pathToRelationalMapper.getQueryMappingInfo(path("dummy")).table;

			assertThat(table).isEqualTo(tableAs("DUMMY_ENTITY", "T0_DUMMYENTITY"));
		}

		@Test
		void getColumnForEntity() {

			Expression column = pathToRelationalMapper.getExpression(path("dummy"));

			assertThat(column).isNull();
		}
	}

	@Nested
	class Sets {

		@Test
		void getTableForEntity() {

			Table table = pathToRelationalMapper.getQueryMappingInfo(path("multi")).table;

			assertThat(table).isEqualTo(tableAs("NESTED_ENTITY", "T0_NESTEDENTITY"));
		}

		@Test
		void getColumnForPropertyInSet() {

			Expression column = pathToRelationalMapper.getExpression(path("multi.string"));

			assertThat(column).isEqualTo(column(
					PathToRelationalMapperUnitTests.this.tableAs("NESTED_ENTITY", "T0_NESTEDENTITY"), "STRING", "T0_C0_STRING"));
		}

		@Test
		void columnForEntityIsActuallyRowNumber() {

			Expression expression = pathToRelationalMapper.getExpression(path("multi"));

			assertThat(expression).isEqualTo(AnalyticFunction.create("ROW_NUMBER")
					.partitionBy(column(tableAs("NESTED_ENTITY", "T0_NESTEDENTITY"), "DUMMY_ENTITY")).as("T0_RN"));
		}
	}

	private PersistentPropertyPath<RelationalPersistentProperty> path(String path) {
		return mappingContext.getPersistentPropertyPath(path, DummyEntity.class);
	}

	private Column column(Table table, String columnName) {
		return table.column(SqlIdentifier.quoted(columnName));
	}

	private Column column(Table table, String columnName, String alias) {
		return table.column(SqlIdentifier.quoted(columnName)).as(alias);
	}

	private Table tableAs(String table, String alias) {
		return Table.create(SqlIdentifier.quoted(table)).as(alias);
	}

	private static class DummyEntity {
		String string;
		DummyEntity dummy;
		Set<NestedEntity> multi;
	}

	private static class NestedEntity {
		String string;
	}
}
