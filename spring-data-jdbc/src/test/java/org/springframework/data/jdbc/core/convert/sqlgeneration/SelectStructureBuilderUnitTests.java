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
import static org.assertj.core.api.SoftAssertions.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.SelectStructure.FunctionSpec.*;

import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.sql.SqlIdentifier;

class SelectStructureBuilderUnitTests {

	private RelationalMappingContext context = new JdbcMappingContext(NamingStrategy.INSTANCE);

	@Test
	void createSingleEntityStructure() {

		SelectStructure structure = new SelectStructureBuilder(context).of(DummyEntity.class);

		assertThat(structure.getTop()).isInstanceOf(SelectStructure.TableStructure.class);

		List<SelectStructure.TableStructure> leafNodes = structure.getLeafTables();

		assertThat(leafNodes).hasSize(1);
		SelectStructure.TableStructure tableStructure = leafNodes.get(0);

		assertSoftly(softly -> {

			softly.assertThat(tableStructure.tableName()).isEqualTo(SqlIdentifier.quoted("DUMMY_ENTITY"));
			softly.assertThat(tableStructure.parent()).isNull();
			softly.assertThat(tableStructure.columns()).extracting(c -> c.columnIdentifier)
					.containsExactlyInAnyOrder(SqlIdentifier.quoted("NAME"));
		});
	}

	@Test
	void embeddedMapsToSameTable() {

		SelectStructure structure = new SelectStructureBuilder(context).of(WithEmbedded.class);

		assertThat(structure.getTop()).isInstanceOf(SelectStructure.TableStructure.class);

		List<SelectStructure.TableStructure> leafNodes = structure.getLeafTables();

		assertThat(leafNodes).hasSize(1);
		SelectStructure.TableStructure tableStructure = leafNodes.get(0);

		assertSoftly(softly -> {

			softly.assertThat(tableStructure.tableName()).isEqualTo(SqlIdentifier.quoted("WITH_EMBEDDED"));
			softly.assertThat(tableStructure.parent()).isNull();
			softly.assertThat(tableStructure.columns()).extracting(c -> c.columnIdentifier, c -> c.alias)
					.containsExactlyInAnyOrder(
							Tuple.tuple(SqlIdentifier.quoted("WITH_EMBEDDED_NAME"), SqlIdentifier.unquoted("T0_C0_WITHEMBEDDEDNAME")),
							Tuple.tuple(SqlIdentifier.quoted("NAME"), SqlIdentifier.unquoted("T0_C1_NAME")));

		});
	}

	@Test
	void singleReferenceMapsToSecondaryTable() {

		SelectStructure structure = new SelectStructureBuilder(context).of(WithJoin.class);

		assertThat(structure.getTop()).isInstanceOf(SelectStructure.TableStructure.class);

		List<SelectStructure.TableStructure> leafNodes = structure.getLeafTables();

		assertThat(leafNodes).hasSize(1);
		SelectStructure.TableStructure tableStructure = leafNodes.get(0);

		assertSoftly(softly -> {

			softly.assertThat(tableStructure.tableName()).isEqualTo(SqlIdentifier.quoted("WITH_JOIN"));
			softly.assertThat(tableStructure.alias()).isEqualTo(SqlIdentifier.unquoted("T0_WITHJOIN"));
			softly.assertThat(tableStructure.parent()).isNull();
			softly.assertThat(tableStructure.columns()).extracting(c -> c.columnIdentifier)
					.containsExactlyInAnyOrder(SqlIdentifier.quoted("WITH_JOIN_NAME"));

			SelectStructure.TableStructure joinTable = tableStructure.joins().iterator().next().table();
			softly.assertThat(joinTable.tableName()).isEqualTo(SqlIdentifier.quoted("DUMMY_ENTITY"));
			softly.assertThat(joinTable.alias()).isEqualTo(SqlIdentifier.unquoted("T1_DUMMYENTITY"));
			softly.assertThat(joinTable.columns()).extracting(c -> c.columnIdentifier)
					.containsExactlyInAnyOrder(SqlIdentifier.quoted("NAME"));

		});
	}

	@Test
	void collectionGetsMappedToAnalyticJoinParent() {

		SelectStructure structure = new SelectStructureBuilder(context).of(WithCollection.class);
		List<SelectStructure.TableStructure> leafNodes = structure.getLeafTables();

		assertThat(leafNodes).hasSize(1);
		SelectStructure.TableStructure tableStructure = leafNodes.get(0);


		assertThat(structure.getTop()).isInstanceOf(SelectStructure.AnalyticJoinStructure.class);

		assertSoftly(softly -> {

			softly.assertThat(tableStructure.tableName()).isEqualTo(SqlIdentifier.quoted("WITH_COLLECTION"));
			softly.assertThat(tableStructure.alias()).isEqualTo(SqlIdentifier.unquoted("T0_WITHCOLLECTION"));
			softly.assertThat(tableStructure.columns()) //
					.extracting(c -> c.columnIdentifier, c -> c.alias, c -> c.function) //
					.containsExactlyInAnyOrder( //
							tuple(SqlIdentifier.quoted("WITH_COLLECTION_NAME"), SqlIdentifier.unquoted("T0_C0_WITHCOLLECTIONNAME"),
									NONE) //
			);

			SelectStructure.AnalyticJoinStructure join = tableStructure.parent();
			assertThat(join).isNotNull();

			softly.assertThat(join.alias()).isEqualTo(SqlIdentifier.unquoted("V0"));
			softly.assertThat(join.columns()).extracting(c -> c.columnIdentifier, c -> c.alias, c -> c.function) //
					.containsExactlyInAnyOrder( //
							tuple(SqlIdentifier.unquoted("T1_C2_NAME"), null, NONE), //
							tuple(SqlIdentifier.unquoted("T1_C0_WITHCOLLECTION"), null, NONE), //
							tuple(SqlIdentifier.unquoted("T1_C1_RN"), null, ROW_NUMBER), //
							tuple(SqlIdentifier.unquoted("T0_C0_WITHCOLLECTIONNAME"), null, NONE) //
			);

			softly.assertThat(join.firstTable()).isEqualTo(tableStructure);
			SelectStructure.TableStructure secondTable = join.secondTable();
			softly.assertThat(secondTable.tableName()).isEqualTo(SqlIdentifier.quoted("DUMMY_ENTITY"));
			softly.assertThat(secondTable.alias()).isEqualTo(SqlIdentifier.unquoted("T1_DUMMYENTITY"));
			softly.assertThat(secondTable.columns()) //
					.extracting(c -> c.columnIdentifier, c -> c.alias, c -> c.function) //
					.containsExactlyInAnyOrder( //
							tuple(SqlIdentifier.quoted("NAME"), SqlIdentifier.unquoted("T1_C2_NAME"), NONE), // normal property
							tuple(SqlIdentifier.quoted("WITH_COLLECTION"), SqlIdentifier.unquoted("T1_C0_WITHCOLLECTION"), NONE), // back reference
							tuple(SqlIdentifier.quoted("WITH_COLLECTION"), SqlIdentifier.unquoted("T1_C1_RN"), ROW_NUMBER) // row number
			);

		});
	}

	static class DummyEntity {
		String name;
	}

	static class WithEmbedded {
		String withEmbeddedName;
		@Embedded(onEmpty = Embedded.OnEmpty.USE_NULL) DummyEntity dummy;
	}

	static class WithJoin {
		String withJoinName;
		DummyEntity dummy;
	}

	static class WithCollection {
		String withCollectionName;
		List<DummyEntity> dummies;
	}

}
