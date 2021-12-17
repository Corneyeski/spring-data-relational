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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.lang.Nullable;

public class SelectStructure {

	final Map<PersistentPropertyPath<RelationalPersistentProperty>, TableStructure> tables = new HashMap<>();

	SelectStructure(RelationalPersistentEntity<?> root, String alias) {
		this.tables.put(null, new SelectStructure.TableStructure(root, SqlIdentifier.unquoted(alias)));
	}

	public List<TableStructure> getLeafTables() {
		return Collections.singletonList(tables.get(null));
	}

	TableStructure getAncesterTableFor(PersistentPropertyPath<RelationalPersistentProperty> path) {
		while (!tables.containsKey(path)) {
			if (path.isEmpty()) {
				return tables.get(null);
			}
			path = path.getParentPath();
		}

		return tables.get(path);
	}

	public Object getTop() {
		return getTop(getLeafTables().get(0));
	}

	private Object getTop(TableStructure tableStructure) {
		return tableStructure.parent() != null ?  tableStructure.parent : tableStructure;
	}

	public class TableStructure {

		private final RelationalPersistentEntity<?> entity;
		private final SqlIdentifier alias;
		private final Set<SelectStructure.ColumnStructure> columns = new HashSet<>();
		private final Map<PersistentPropertyPath<RelationalPersistentProperty>, JoinStructure> joins = new HashMap<>();
		private AnalyticJoinStructure parent;
		private ColumnStructure id;
		private ColumnStructure rowNumber;
		private ColumnStructure backReference;

		TableStructure(RelationalPersistentEntity<?> entity, SqlIdentifier alias) {

			this.entity = entity;
			this.alias = alias;
		}

		public ColumnStructure rowNumber() {
			return rowNumber;
		}
		ColumnStructure backReference() {
			return backReference;
		}

		SqlIdentifier tableName() {
			return entity.getTableName();
		}

		@Nullable
		AnalyticJoinStructure parent() {
			return parent;
		}

		public Iterable<SelectStructure.ColumnStructure> columns() {
			return columns;
		}

		ColumnStructure addColumn(PersistentPropertyPath<RelationalPersistentProperty> path, SqlIdentifier columnIdentifier,
								  SqlIdentifier alias) {
			final ColumnStructure columnStructure = new ColumnStructure(columnIdentifier, alias);
			columns.add(columnStructure);
			return columnStructure;
		}

		void addId(PersistentPropertyPath<RelationalPersistentProperty> path, SqlIdentifier columnIdentifier, SqlIdentifier alias) {
			id = addColumn(path, columnIdentifier, alias);
		}

		void addBackReference(SqlIdentifier backReferenceName, SqlIdentifier alias) {

			backReference = new ColumnStructure(backReferenceName, alias);
			columns.add(this.backReference);

		}

		void addRowNumber(SqlIdentifier backReference, SqlIdentifier alias) {

			rowNumber = new ColumnStructure(FunctionSpec.ROW_NUMBER, backReference, alias);
			columns.add(rowNumber);
		}

		void addJoin(PersistentPropertyPath<RelationalPersistentProperty> path, RelationalPersistentEntity<?> entity,
				SqlIdentifier alias) {

			JoinStructure join = new JoinStructure(path, entity, alias);
			joins.put(path, join);
			tables.put(path, join.table);
		}

		Iterable<JoinStructure> joins() {
			return joins.values();
		}

		SqlIdentifier alias() {
			return alias;
		}

		void addAnalyticJoin(PersistentPropertyPath<RelationalPersistentProperty> path, SqlIdentifier viewAlias,
				RelationalPersistentEntity<?> entity, SqlIdentifier tableAlias, SqlIdentifier backReference,
				SqlIdentifier backReferenceAlias, SqlIdentifier rowNumberAlias) {

			AnalyticJoinStructure join = new AnalyticJoinStructure(path, viewAlias, this, entity, tableAlias, backReference,
					backReferenceAlias, rowNumberAlias);
			this.parent = join;
			tables.put(path, join.secondTable());
		}

		public ColumnStructure id() {
			return id;
		}


		class JoinStructure {

			private final PersistentPropertyPath<RelationalPersistentProperty> path;
			private final TableStructure table;

			JoinStructure(PersistentPropertyPath<RelationalPersistentProperty> path, RelationalPersistentEntity<?> entity,
					SqlIdentifier alias) {
				this.path = path;
				table = new TableStructure(entity, alias);
			}

			TableStructure table() {
				return table;
			}

			PersistentPropertyPath<RelationalPersistentProperty> path() {
				return path;
			}
		}
	}

	class AnalyticJoinStructure {

		private final PersistentPropertyPath<RelationalPersistentProperty> path;
		private final TableStructure firstTable;
		private final TableStructure secondTable;
		private final SqlIdentifier viewAlias;
		private final Set<ColumnStructure> columns = new HashSet<>();

		AnalyticJoinStructure(PersistentPropertyPath<RelationalPersistentProperty> path, SqlIdentifier viewAlias,
				TableStructure firstTable, RelationalPersistentEntity<?> entity, SqlIdentifier tableAlias,
				SqlIdentifier backReference, SqlIdentifier backReferenceAlias, SqlIdentifier rowNumberAlias) {

			this.path = path;
			this.firstTable = firstTable;
			this.viewAlias = viewAlias;

			this.secondTable = new TableStructure(entity, tableAlias);
			secondTable.addBackReference(backReference, backReferenceAlias);
			secondTable.addRowNumber(backReference, rowNumberAlias);
		}

		PersistentPropertyPath<RelationalPersistentProperty> path() {
			return path;
		}

		TableStructure firstTable() {
			return firstTable;
		}

		TableStructure secondTable() {
			return secondTable;
		}

		SqlIdentifier alias() {
			return viewAlias;
		}

		Iterable<ColumnStructure> columns() {

			if (columns.isEmpty()) {

				firstTable.columns.forEach(c -> columns.add(new ColumnStructure(c.function, c.alias, null)));
				secondTable.columns.forEach(c -> columns.add(new ColumnStructure(c.function, c.alias, null)));
			}

			return columns;
		}
	}

	enum FunctionSpec {
		NONE, ROW_NUMBER
	}

	static class ColumnStructure {

		final SqlIdentifier columnIdentifier;
		final SqlIdentifier alias;
		final FunctionSpec function;

		ColumnStructure(SqlIdentifier columnIdentifier, @Nullable SqlIdentifier alias) {
			this(FunctionSpec.NONE, columnIdentifier, alias);
		}

		ColumnStructure(FunctionSpec function, SqlIdentifier columnIdentifier, @Nullable SqlIdentifier alias) {

			this.function = function;
			this.columnIdentifier = columnIdentifier;
			this.alias = alias;
		}
	}
}
