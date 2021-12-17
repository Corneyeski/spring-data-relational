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

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.AnalyticFunction;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Comparison;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.core.sql.TableLike;
import org.springframework.lang.Nullable;

/**
 * Maps entities, paths and properties to tables, columns, owning tables, joining tables and join conditions. It creates
 * the relational structure for entities and paths.
 *
 * @author Jens Schauder
 * @since 2.4
 */
public class PathToRelationalMapper {

	private final RelationalPersistentEntity<?> entity;
	private final AliasFactory aliasFactory;
	private final PathAnalyzer pathAnalyzer;

	private final Map<RelationalPersistentEntity<?>, Table> tables = new HashMap<>();

	/**
	 * @param entity the aggregate root on which all paths are assumed to be based on. Must not be {@literal null}.
	 * @param aliasFactory factory used to create aliases for tables and columns in oder to avoid name collisions. Must
	 *          not be {@literal null}.
	 * @param pathAnalyzer utility to extract information from entity paths. Must not be {@literal null}.
	 */
	public PathToRelationalMapper(RelationalPersistentEntity<?> entity, AliasFactory aliasFactory, PathAnalyzer pathAnalyzer) {

		this.entity = entity;
		this.aliasFactory = aliasFactory;
		this.pathAnalyzer = pathAnalyzer;
	}

	public QueryMappingInfo getQueryMappingInfo(PersistentPropertyPath<RelationalPersistentProperty> path) {

		Expression expression = getExpression(path);
		Condition joinCondition = null;

		if (expression instanceof Column) {
			final TableLike table = ((Column) expression).getTable();
			assert table != null;
			if (!table.equals(getTable())) {
				joinCondition = getJoinConditionFromParent(path, table);
			}
		}

		return new QueryMappingInfo(expression, pathAnalyzer.getTableOwner(path).getTableName(),
				aliasFactory.getAlias(pathAnalyzer.getTableOwner(path)), joinCondition);
	}

	/**
	 * @return the table used for persisting the aggregate root. Guaranteed to be not {@literal null}.
	 */
	public Table getTable() {
		return getTable(entity);
	}

	/**
	 * If the provided {@link PersistentPropertyPath} maps to a column the {@link Table} returned is the table to which
	 * that column belongs. If not it is the {@link Table} to which all its properties get mapped.
	 *
	 * @param path must not be {@literal null}.
	 * @return returns the {@link Table} to which this path belongs. Guaranteed to be not {@literal null}.
	 */
	private Table getTable(PersistentPropertyPath<RelationalPersistentProperty> path) {
		return getTable(pathAnalyzer.getTableOwner(path));
	}

	/**
	 * When the provided path maps to a database column that column is returned, complete with table information and
	 * alias. If no such column exists, because the path represents and embeddable or a references, either {@literal null}
	 * is returned or an expression that should be included in the query, but does not map to a column or property.
	 *
	 * @param path for which to obtain the column.
	 * @return {@literal null} or the {@link Column} mapped to the provided path or a {@link Expression}.
	 */
	@Nullable
	Expression getExpression(PersistentPropertyPath<RelationalPersistentProperty> path) {

		RelationalPersistentProperty property = path.getRequiredLeafProperty();
		if (property.isCollectionLike()) {

			SqlIdentifier reverseColumnName = pathAnalyzer.getReverseColumnName(path);
			Table table = getTable(path);
			Column reverseColumn = table.column(reverseColumnName);

			return AnalyticFunction.create("ROW_NUMBER").partitionBy(reverseColumn).as(aliasFactory.getAlias(path) + "_RN");
		}
		if (property.isEmbedded() || property.isEntity()) {
			return null;
		}
		return doGetColumn(path);
	}

	private Column getParentIdColumn(PersistentPropertyPath<RelationalPersistentProperty> path) {

		SqlIdentifier idColumn = getIdColumnName(path);

		TableLike parentTable = getTable(path);

		return parentTable.column(idColumn);

	}

	SqlIdentifier getTableName(PersistentPropertyPath<RelationalPersistentProperty> path) {
		return pathAnalyzer.getTableOwner(path).getTableName();
	}

	private Table getTable(RelationalPersistentEntity<?> entity) {

		return tables.computeIfAbsent(entity, e -> {
			SqlIdentifier tableName = e.getTableName();
			return Table.create(tableName).as(aliasFactory.getAlias(entity));
		});
	}

	private Column doGetColumn(PersistentPropertyPath<RelationalPersistentProperty> path) {

		return getTable(path) //
				.column(getColumnIdentifier(path)) //
				.as(aliasFactory.getAlias(path));
	}

	SqlIdentifier getColumnIdentifier(PersistentPropertyPath<RelationalPersistentProperty> path) {

		SqlIdentifier baseColumnName = path.getRequiredLeafProperty().getColumnName();
		return baseColumnName.transform(name -> getEmbeddedPrefix(path) + name);
	}

	private String getEmbeddedPrefix(PersistentPropertyPath<RelationalPersistentProperty> path) {

		if (path.isEmpty()) {
			return "";
		}

		RelationalPersistentProperty property = path.getRequiredLeafProperty();
		PersistentPropertyPath<RelationalPersistentProperty> parentPath = path.getParentPath();
		if (property.isEmbedded()) {

			String prefix = property.getEmbeddedPrefix();
			prefix = prefix == null ? "" : prefix;

			if (pathAnalyzer.isEmbedded(parentPath)) {
				return getEmbeddedPrefix(parentPath) + prefix;
			}
			return prefix;
		} else {
			return getEmbeddedPrefix(parentPath);
		}
	}

	SqlIdentifier getIdColumnName(PersistentPropertyPath<RelationalPersistentProperty> parentPath) {

		SqlIdentifier idColumn;
		if (parentPath.isEmpty()) {
			idColumn = entity.getIdColumn();
		} else {

			RelationalPersistentEntity<?> owner = parentPath.getRequiredLeafProperty().getOwner();
			if (owner.hasIdProperty()) {
				idColumn = owner.getIdColumn();
			} else {
				idColumn = pathAnalyzer.getReverseColumnName(parentPath);
			}
		}
		return idColumn;
	}

	Condition getJoinConditionFromParent(PersistentPropertyPath<RelationalPersistentProperty> path, TableLike columnTable) {

		PersistentPropertyPath<RelationalPersistentProperty> parentPath = path.getParentPath();

		return getJoinConditionFromPath(parentPath, columnTable);
	}

	Comparison getJoinConditionFromPath(PersistentPropertyPath<RelationalPersistentProperty> parentPath, TableLike columnTable) {

		Column parentIdColumn = getParentIdColumn(parentPath);
		return parentIdColumn.isEqualTo(columnTable.column(pathAnalyzer.getReverseColumnName(parentPath)));
	}

	public static class QueryMappingInfo {

		public final Expression expression;
		final SqlIdentifier tableIdentifier;
		final String tableAlias;
		public final Condition joinCondition;

		final Column column;
		public final Table table;

		QueryMappingInfo(@Nullable Expression expression, SqlIdentifier tableIdentifier, String tableAlias,
				@Nullable Condition joinCondition) {

			this.expression = expression;

			this.tableIdentifier = tableIdentifier;
			this.tableAlias = tableAlias;
			this.joinCondition = joinCondition;

			this.table = Table.create(tableIdentifier).as(tableAlias);
			this.column = (expression instanceof Column) ? (Column) expression : null;
		}
	}
}
