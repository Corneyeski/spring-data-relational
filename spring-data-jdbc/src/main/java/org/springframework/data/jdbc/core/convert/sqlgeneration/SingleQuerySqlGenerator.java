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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.convert.Identifier;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.RenderContextFactory;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.*;
import org.springframework.data.relational.core.sql.render.SqlRenderer;
import org.springframework.util.Assert;

/**
 * A SQL generator generating single queries to load complete aggregates.
 * 
 * @author Jens Schauder
 * @since 2.4
 */
public class SingleQuerySqlGenerator extends WritingSqlGenerator {

	private final RelationalPersistentEntity<?> entity;
	private final RelationalMappingContext mappingContext;

	private final SqlRenderer sqlRenderer;
	private final PathToRelationalMapper pathToRelationalMapper;

	public SingleQuerySqlGenerator(RelationalMappingContext mappingContext, JdbcConverter converter,
								   RelationalPersistentEntity<?> entity, Dialect dialect) {

  		super(entity, mappingContext, converter, dialect);

		this.mappingContext = mappingContext;
		this.entity = entity;
		this.sqlRenderer = SqlRenderer.create(new RenderContextFactory(dialect).createRenderContext());
		final PathAnalyzer pathAnalyzer = new PathAnalyzer(mappingContext, entity);
		this.pathToRelationalMapper = new PathToRelationalMapper(entity, new AliasFactory(pathAnalyzer), pathAnalyzer);
	}

	@Override
	public String getFindAllInList() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getFindAll() {

		SelectStructureBuilder builder = new SelectStructureBuilder(mappingContext);
		SelectStructure structure = builder.of(entity);

		final Object top = structure.getTop();
		if ((top instanceof SelectStructure.TableStructure)) {

			final Select select = selectFromTableStructure((SelectStructure.TableStructure) top);
			return sqlRenderer.render(select);
		} else {

			Assert.state(top instanceof SelectStructure.AnalyticJoinStructure,
					"What else can this be but a AnalyticJoinStructure?");

			SelectStructure.AnalyticJoinStructure analyticJoinStructure = (SelectStructure.AnalyticJoinStructure) top;

			final SelectStructure.TableStructure firstTable = analyticJoinStructure.firstTable();
			final SelectStructure.TableStructure secondTable = analyticJoinStructure.secondTable();

			final InlineQuery secondSelect = InlineQuery.create(selectFromTableStructure(secondTable),
					"IV0");
			final InlineQuery firstSelect = InlineQuery.create(selectFromTableStructure(firstTable),
					"IV1");

			Set<Expression> columns = new HashSet<>();

			for (SelectStructure.ColumnStructure column : analyticJoinStructure.columns()) {
				columns.add(columnFromAlias(column.columnIdentifier));
			}

			Expression id;
			Expression backreference;
			Condition idToBackReferenceCondition = Conditions.isEqual(columnFromAlias(firstTable.id().alias), columnFromAlias(secondTable.backReference().alias));
			final Comparison rowNumberCondition = Conditions.isEqual(SQL.literalOf(1),
					columnFromAlias(secondTable.rowNumber().alias));

			final SelectBuilder.SelectFromAndJoinCondition join = Select.builder().select(columns) //
					.from(firstSelect) //
					.fullOuterJoin(secondSelect) //
					.on(rowNumberCondition.and(idToBackReferenceCondition));

			return sqlRenderer.render(join.build());
		}

	}

	@Override
	public String getFindAll(Sort sort) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getFindAll(Pageable pageable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getFindAllByProperty(Identifier parentIdentifier, SqlIdentifier keyColumn, boolean ordered) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getExists() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getFindOne() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getAcquireLockById(LockMode lockMode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getAcquireLockAll(LockMode lockMode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getCount() {
		throw new UnsupportedOperationException();
	}

	private Expression columnFromAlias(SqlIdentifier columnIdentifier) {
		// that is hacky. We really should have columns without tables, shouldn't we?
		return Expressions.just(columnIdentifier.getReference());
	}

	private Select selectFromTableStructure(SelectStructure.TableStructure top) {

		Table table = Table.create(top.tableName()).as(top.alias());
		List<Expression> expressions = new ArrayList<>();
		Map<TableLike, Condition> oneToOneJoins = new LinkedHashMap<>();
		addColumns(expressions, oneToOneJoins, top);

		SelectBuilder.SelectJoin baseSelect = createBaseSelect(table, expressions, oneToOneJoins);

		return baseSelect.build();
	}

	private SelectBuilder.SelectJoin createBaseSelect(Table table, List<Expression> expressions,
			Map<TableLike, Condition> oneToOneJoins) {

		SelectBuilder.SelectJoin baseSelect = Select.builder() //
				.select(expressions) //
				.from(table);

		for (Map.Entry<TableLike, Condition> join : oneToOneJoins.entrySet()) {
			baseSelect = baseSelect.leftOuterJoin(join.getKey()).on(join.getValue());
		}

		return baseSelect;
	}

	private void addColumns(List<Expression> expressions, Map<TableLike, Condition> oneToOneJoins,
			SelectStructure.TableStructure tableStructure) {

		for (SelectStructure.ColumnStructure columnStructure : tableStructure.columns()) {

			expressions.add(constructColumn(tableStructure, columnStructure));
		}

		for (SelectStructure.TableStructure.JoinStructure join : tableStructure.joins()) {

			SelectStructure.TableStructure joinTableStructure = join.table();

			Table table = Table.create(joinTableStructure.tableName()).as(joinTableStructure.alias());
			PersistentPropertyPath<RelationalPersistentProperty> path = join.path();
			Condition condition = pathToRelationalMapper.getJoinConditionFromPath(path, table);
			oneToOneJoins.put(table, condition);

			addColumns(expressions, oneToOneJoins, joinTableStructure);

		}
	}

	private Expression constructColumn(SelectStructure.TableStructure tableStructure,
			SelectStructure.ColumnStructure columnStructure) {

		Table table = Table.create(tableStructure.tableName()).as(tableStructure.alias());
		final Column column = table.column(columnStructure.columnIdentifier).as(columnStructure.alias);

		if (columnStructure.function == SelectStructure.FunctionSpec.ROW_NUMBER) {
			return AnalyticFunction.create("ROW_NUMBER").partitionBy(column).as(columnStructure.alias);
		}

		return column;
	}
}
