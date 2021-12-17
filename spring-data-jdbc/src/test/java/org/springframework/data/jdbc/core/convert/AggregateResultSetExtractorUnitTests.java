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

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

public class AggregateResultSetExtractorUnitTests {

	RelationalMappingContext context = new JdbcMappingContext(NamingStrategy.INSTANCE);
	private RelationalPersistentEntity rootEntity = context.getRequiredPersistentEntity(SimpleEntity.class);
	private JdbcConverter converter = new BasicJdbcConverter(context, mock(RelationResolver.class));

	AggregateResultSetExtractor<SimpleEntity> extractor = new AggregateResultSetExtractor<>(context, rootEntity,
			converter, this::column);

	@Test
	void emptyResultSetYieldsEmptyResult() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(asList("T0_C0_ID1", "T0_C1_NAME"));
		assertThat(extractor.extractData(resultSet)).isEmpty();
	}

	@Test
	void singleSimpleEntityGetsExtractedFromSingleRow() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(asList(column("id1"), column("name")), 1, "Alfred");
		assertThat(extractor.extractData(resultSet)).extracting(e -> e.id1, e -> e.name)
				.containsExactly(tuple(1L, "Alfred"));
	}

	@Test
	void multipleSimpleEntitiesGetExtractedFromMultipleRows() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(asList(column("id1"), column("name")), //
				1, "Alfred", //
				2, "Bertram" //
		);
		assertThat(extractor.extractData(resultSet)).extracting(e -> e.id1, e -> e.name).containsExactly( //
				tuple(1L, "Alfred"), //
				tuple(2L, "Bertram") //
		);
	}

	@Test
	void entityReferenceGetsExtractedFromSingleRow() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(
				asList(column("id1"), column("dummy"), column("dummy.dummyName")), //
				1, 1, "Dummy Alfred");

		assertThat(extractor.extractData(resultSet)).extracting(e -> e.id1, e -> e.dummy.dummyName)
				.containsExactly(tuple(1L, "Dummy Alfred"));
	}

	@Test
	void nullEntityReferenceGetsExtractedFromSingleRow() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(
				asList(column("id1"), column("dummy"), column("dummy.dummyName")), //
				1, null, "Dummy Alfred");

		assertThat(extractor.extractData(resultSet)).extracting(e -> e.id1, e -> e.dummy).containsExactly(tuple(1L, null));
	}

	@Test
	void extractSingleSetReference() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(
				asList(column("id1"), column("dummies"), column("dummies.dummyName")), //
				1, 1, "Dummy Alfred", //
				1, 2, "Dummy Berta", //
				1, 3, "Dummy Carl");

		final Iterable<SimpleEntity> result = extractor.extractData(resultSet);
		assertThat(result).extracting(e -> e.id1).containsExactly(1L);
		assertThat(result.iterator().next().dummies).extracting(d -> d.dummyName) //
				.containsExactlyInAnyOrder("Dummy Alfred", "Dummy Berta", "Dummy Carl");
	}

	@Test
	void extractMultipleSetReference() throws SQLException {

		ResultSet resultSet = ResultSetTestUtil.mockResultSet(
				asList(column("id1"), //
						column("dummies"), column("dummies.dummyName"), //
						column("otherDummies"), column("otherDummies.dummyName")), //
				1, 1, "Dummy Alfred",1, "Other Ephraim", //
				1, 2, "Dummy Berta",2, "Other Zeno", //
				1, 3, "Dummy Carl", null, null);

		final Iterable<SimpleEntity> result = extractor.extractData(resultSet);
		assertThat(result).extracting(e -> e.id1).containsExactly(1L);
		assertThat(result.iterator().next().dummies).extracting(d -> d.dummyName) //
				.containsExactlyInAnyOrder("Dummy Alfred", "Dummy Berta", "Dummy Carl");
		assertThat(result.iterator().next().otherDummies).extracting(d -> d.dummyName) //
				.containsExactlyInAnyOrder("Other Ephraim", "Other Zeno");
	}

	private String column(String path) {

		final PersistentPropertyPath<RelationalPersistentProperty> propertyPath = context.getPersistentPropertyPath(path,
				SimpleEntity.class);

		return column(propertyPath);
	}

	private String column(PersistentPropertyPath<RelationalPersistentProperty> propertyPath) {
		return propertyPath.toDotPath();
	}

	private static class SimpleEntity {
		@Id long id1;
		String name;
		DummyEntity dummy;

		Set<DummyEntity> dummies;
		Set<DummyEntity> otherDummies;
	}

	private static class DummyEntity {
		String dummyName;
	}
}
