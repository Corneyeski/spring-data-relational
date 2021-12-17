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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.*;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitorAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.convert.BasicJdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

public class SingleQuerySqlGeneratorUnitTests {

	private NamingStrategy namingStrategy = NamingStrategy.INSTANCE;
	private RelationalMappingContext context = new JdbcMappingContext(namingStrategy);
	private JdbcConverter converter = new BasicJdbcConverter(context, (identifier, path) -> {
		throw new UnsupportedOperationException();
	});

	@Test
	void findAllSimpleEntity() throws JSQLParserException {

		SingleQuerySqlGenerator generator = createGenerator(SimpleEntity.class);

		String findAll = generator.getFindAll();

		assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
				entry("T0_C0_ID1", "T0_SIMPLEENTITY.\"ID1\""), //
				entry("T0_C1_NAME", "T0_SIMPLEENTITY.\"NAME\"")) //
		);

		assertThat(findAll).endsWith("FROM \"SIMPLE_ENTITY\" T0_SIMPLEENTITY");

	}

	@Test
	void findAllWithEmbedded() {

		SingleQuerySqlGenerator generator = createGenerator(WithEmbedded.class);

		String findAll = generator.getFindAll();

		assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
				entry("T0_C0_ID2", "T0_WITHEMBEDDED.\"ID2\""), //
				entry("T0_C1_NAME", "T0_WITHEMBEDDED.\"EMB_NAME\""), //
				entry("T0_C2_ALONG", "T0_WITHEMBEDDED.\"EMB_A_LONG\"")) //
		);

		assertThat(findAll).endsWith("FROM \"WITH_EMBEDDED\" T0_WITHEMBEDDED");
	}

	@Test
	void findAllWithEmbeddedEmbedded() {

		SingleQuerySqlGenerator generator = createGenerator(WithEmbeddedEmbedded.class);

		String findAll = generator.getFindAll();

		assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
				entry("T0_C0_ID3", "T0_WITHEMBEDDEDEMBEDDED.\"ID3\""), //
				entry("T0_C1_ID2", "T0_WITHEMBEDDEDEMBEDDED.\"OUTER_ID2\""), //
				entry("T0_C2_NAME", "T0_WITHEMBEDDEDEMBEDDED.\"OUTER_EMB_NAME\""), //
				entry("T0_C3_ALONG", "T0_WITHEMBEDDEDEMBEDDED.\"OUTER_EMB_A_LONG\"")) //
		);

		assertThat(findAll).endsWith("FROM \"WITH_EMBEDDED_EMBEDDED\" T0_WITHEMBEDDEDEMBEDDED");
	}

	@Test
	void findAllWithOneToOne() {

		SingleQuerySqlGenerator generator = createGenerator(WithOneToOne.class);

		String findAll = generator.getFindAll();

		assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
				entry("T0_C0_ID4", "T0_WITHONETOONE.\"ID4\""), //
				entry("T1_C0_ID1", "T1_SIMPLEENTITY.\"ID1\""), //
				entry("T1_C1_NAME", "T1_SIMPLEENTITY.\"NAME\"")) //
		);

		assertThat(findAll).endsWith("FROM \"WITH_ONE_TO_ONE\" T0_WITHONETOONE " + //
				"LEFT OUTER JOIN \"SIMPLE_ENTITY\" T1_SIMPLEENTITY " + //
				"ON T0_WITHONETOONE.\"ID4\" = T1_SIMPLEENTITY.\"WITH_ONE_TO_ONE\"");
	}

	@Test
	void findAllWithNestedOneToOne() {

		SingleQuerySqlGenerator generator = createGenerator(WithOneToOneWithOneToOne.class);

		String findAll = generator.getFindAll();

		assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
				entry("T0_C0_ID5", "T0_WITHONETOONEWITHONETOONE.\"ID5\""), //
				entry("T1_C0_ID4", "T1_WITHONETOONE.\"ID4\""), //
				entry("T2_C0_ID1", "T2_SIMPLEENTITY.\"ID1\""), //
				entry("T2_C1_NAME", "T2_SIMPLEENTITY.\"NAME\"")) //
		);

		assertThat(findAll).endsWith( //
				"FROM \"WITH_ONE_TO_ONE_WITH_ONE_TO_ONE\" T0_WITHONETOONEWITHONETOONE " + //
						"LEFT OUTER JOIN \"WITH_ONE_TO_ONE\" T1_WITHONETOONE " + //
						"ON T0_WITHONETOONEWITHONETOONE.\"ID5\" = T1_WITHONETOONE.\"WITH_ONE_TO_ONE_WITH_ONE_TO_ONE\" " + //
						"LEFT OUTER JOIN \"SIMPLE_ENTITY\" T2_SIMPLEENTITY " + //
						"ON T1_WITHONETOONE.\"ID4\" = T2_SIMPLEENTITY.\"WITH_ONE_TO_ONE\"");
	}

	@Test
	void findAllWithNestedOneToOneWoId() {

		SingleQuerySqlGenerator generator = createGenerator(WithOneToOneWithOneToOneWoId.class);

		String findAll = generator.getFindAll();

		assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
				entry("T0_C0_ID6", "T0_WITHONETOONEWITHONETOONEWOID.\"ID6\""), //
				entry("T1_C0_VALUE", "T1_WITHONETOONEWOID.\"VALUE\""), //
				entry("T2_C0_ID1", "T2_SIMPLEENTITY.\"ID1\""), //
				entry("T2_C1_NAME", "T2_SIMPLEENTITY.\"NAME\"")) //
		);

		assertThat(findAll).endsWith( //
				"FROM \"WITH_ONE_TO_ONE_WITH_ONE_TO_ONE_WO_ID\" T0_WITHONETOONEWITHONETOONEWOID " + //
						"LEFT OUTER JOIN \"WITH_ONE_TO_ONE_WO_ID\" T1_WITHONETOONEWOID " + //
						"ON T0_WITHONETOONEWITHONETOONEWOID.\"ID6\" = T1_WITHONETOONEWOID.\"WITH_ONE_TO_ONE_WITH_ONE_TO_ONE_WO_ID\" "
						+ //
						"LEFT OUTER JOIN \"SIMPLE_ENTITY\" T2_SIMPLEENTITY " + //
						"ON T1_WITHONETOONEWOID.\"WITH_ONE_TO_ONE_WITH_ONE_TO_ONE_WO_ID\" = T2_SIMPLEENTITY.\"WITH_ONE_TO_ONE_WITH_ONE_TO_ONE_WO_ID\"");
	}

	@Test
	void findAllWithSet() {

		SingleQuerySqlGenerator generator = createGenerator(WithSet.class);

		String findAll = generator.getFindAll();

		assertSoftly(softly -> {
			softly.assertThat(getSelectAliasAndValue(findAll)).containsExactlyInAnyOrderEntriesOf(map( //
					entry("T0_C0_ID7", "T0_C0_ID7"), //
					entry("T1_C0_WITHSET", "T1_C0_WITHSET"), //
					entry("T1_C1_RN", "T1_C1_RN"), //
					entry("T1_C2_ID1", "T1_C2_ID1"), //
					entry("T1_C3_NAME", "T1_C3_NAME")) //
			);

			softly.assertThat(findAll).contains( //
					"FULL OUTER JOIN", //
					"ROW_NUMBER() OVER(PARTITION BY T1_SIMPLEENTITY.\"WITH_SET\") AS T1_C1_RN", //
					"T0_C0_ID7 = T1_C0_WITHSET", //
					"1 = T1_C1_RN");
		});
	}

	private SingleQuerySqlGenerator createGenerator(Class<?> type) {

		return new SingleQuerySqlGenerator(context, converter, context.getPersistentEntity(type), AnsiDialect.INSTANCE);
	}

	private Map<String, String> getSelectAliasAndValue(String query) {
		Select select = null;
		try {
			select = (Select) CCJSqlParserUtil.parse(query);
		} catch (JSQLParserException e) {
			fail("Failed to parse SQL statement", e);
		}

		Map<String, String> map = new HashMap<>();
		for (SelectItem selectItem : ((PlainSelect) select.getSelectBody()).getSelectItems()) {
			selectItem.accept(new SelectItemVisitorAdapter() {
				@Override
				public void visit(SelectExpressionItem item) {
					final Alias alias = item.getAlias();
					map.put(alias == null ? item.getExpression().toString() : alias.getName(), item.getExpression().toString());
				}
			});
		}
		return map;
	}

	private Map<String, String> map(MapEntry<String, String>... entries) {
		final HashMap<String, String> map = new HashMap<>();
		for (MapEntry<String, String> entry : entries) {
			map.put(entry.key, entry.value);
		}
		return map;
	}

	/**
	 * Trivially simple entity with just an id and a simple attribute.
	 */
	private class SimpleEntity {
		@Id long id1;
		String name;
	}

	private class EmbeddedEntity {
		Long aLong;
		String name;
	}

	/**
	 * Entity with just an id and a single embedded attribute.
	 */
	private class WithEmbedded {

		long id2;
		@Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "emb_") EmbeddedEntity embedded;
	}

	/**
	 * Entity with just an id and a single embedded attribute which in turn has an embedded attribute.
	 */
	private class WithEmbeddedEmbedded {

		@Id long id3;
		@Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "outer_") WithEmbedded embedded;
	}

	/**
	 * Entity with just an id and a single joined attribute.
	 */
	private class WithOneToOne {

		@Id long id4;
		SimpleEntity simple;
	}

	/**
	 * Entity with a single joined attribute which has a single joined attribute
	 */
	private class WithOneToOneWithOneToOne {

		@Id long id5;
		WithOneToOne oneToOne;
	}

	/**
	 * Entity with just an id and a single joined attribute.
	 */
	private class WithOneToOneWoId {

		long value;
		SimpleEntity simple;
	}

	/**
	 * Entity with a single joined attribute wo id which has a single joined attribute
	 */
	private class WithOneToOneWithOneToOneWoId {

		@Id long id6;
		WithOneToOneWoId oneToOne;
	}

	private class WithSet {

		@Id long id7;
		Set<SimpleEntity> simples = new HashSet<>();
	}

}
