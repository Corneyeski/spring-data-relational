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

import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.RenderContextFactory;
import org.springframework.data.relational.core.mapping.PersistentPropertyPathExtension;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.*;
import org.springframework.data.relational.core.sql.render.RenderContext;
import org.springframework.data.relational.core.sql.render.SqlRenderer;
import org.springframework.data.util.Lazy;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class WritingSqlGenerator implements SqlGenerator {

	private static final Pattern parameterPattern = Pattern.compile("\\W");
	public static final SqlIdentifier ROOT_ID_PARAMETER = SqlIdentifier.unquoted("rootId");
	public static final SqlIdentifier VERSION_SQL_PARAMETER = SqlIdentifier.unquoted("___oldOptimisticLockingVersion");
	public static final SqlIdentifier ID_SQL_PARAMETER = SqlIdentifier.unquoted("id");
	public static final SqlIdentifier IDS_SQL_PARAMETER = SqlIdentifier.unquoted("ids");


	protected final RelationalPersistentEntity<?> entity;
	protected final MappingContext<RelationalPersistentEntity<?>, RelationalPersistentProperty> mappingContext;
	private final RenderContext renderContext;
	private final SqlContext sqlContext;
	protected final SqlRenderer sqlRenderer;
	protected final Columns columns;
	private final Lazy<String> updateSql = Lazy.of(this::createUpdateSql);
	private final Lazy<String> updateWithVersionSql = Lazy.of(this::createUpdateWithVersionSql);
	private final Lazy<String> deleteByIdSql = Lazy.of(this::createDeleteByIdSql);
	private final Lazy<String> deleteByIdInSql = Lazy.of(this::createDeleteByIdInSql);
	private final Lazy<String> deleteByIdAndVersionSql = Lazy.of(this::createDeleteByIdAndVersionSql);
	private final Lazy<String> deleteByListSql = Lazy.of(this::createDeleteByListSql);

	public WritingSqlGenerator(RelationalPersistentEntity<?> entity, RelationalMappingContext mappingContext, JdbcConverter converter, Dialect dialect) {

		this.entity = entity;
		this.mappingContext = mappingContext;
		this.renderContext = new RenderContextFactory(dialect).createRenderContext();
		this.sqlContext = new SqlContext(entity);
		this.sqlRenderer = SqlRenderer.create(renderContext);
		this.columns = new Columns(entity, mappingContext, converter);
	}

	/**
	 * Construct a IN-condition based on a {@link Select Sub-Select} which selects the ids (or stand ins for ids) of the
	 * given {@literal path} to those that reference the root entities specified by the {@literal rootCondition}.
	 *
	 * @param path specifies the table and id to select
	 * @param rootCondition the condition on the root of the path determining what to select
	 * @param filterColumn the column to apply the IN-condition to.
	 * @return the IN condition
	 */
	private Condition getSubselectCondition(PersistentPropertyPathExtension path,
											Function<Column, Condition> rootCondition, Column filterColumn) {

		PersistentPropertyPathExtension parentPath = path.getParentPath();

		if (!parentPath.hasIdProperty()) {
			if (parentPath.getLength() > 1) {
				return getSubselectCondition(parentPath, rootCondition, filterColumn);
			}
			return rootCondition.apply(filterColumn);
		}

		Table subSelectTable = Table.create(parentPath.getTableName());
		Column idColumn = subSelectTable.column(parentPath.getIdColumnName());
		Column selectFilterColumn = subSelectTable.column(parentPath.getEffectiveIdColumnName());

		Condition innerCondition;

		if (parentPath.getLength() == 1) { // if the parent is the root of the path

			// apply the rootCondition
			innerCondition = rootCondition.apply(selectFilterColumn);
		} else {

			// otherwise we need another layer of subselect
			innerCondition = getSubselectCondition(parentPath, rootCondition, selectFilterColumn);
		}

		Select select = Select.builder() //
				.select(idColumn) //
				.from(subSelectTable) //
				.where(innerCondition).build();

		return filterColumn.in(select);
	}

	protected BindMarker getBindMarker(SqlIdentifier columnName) {
		return SQL.bindMarker(":" + parameterPattern.matcher(renderReference(columnName)).replaceAll(""));
	}

	@Override
	public String getInsert(Set<SqlIdentifier> additionalColumns) {
		return createInsertSql(additionalColumns);
	}

	@Override
	public String getUpdate() {
		return updateSql.get();
	}

	@Override
	public String getUpdateWithVersion() {
		return updateWithVersionSql.get();
	}

	@Override
	public String getDeleteById() {
		return deleteByIdSql.get();
	}

	@Override
	public String getDeleteByIdIn() {
		return deleteByIdInSql.get();
	}

	@Override
	public String
	getDeleteByIdAndVersion() {
		return deleteByIdAndVersionSql.get();
	}

	/**
	 * Create a {@code DELETE FROM … WHERE :ids in (…)} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getDeleteByList() {
		return deleteByListSql.get();
	}

	@Override
	public String createDeleteAllSql(@Nullable PersistentPropertyPath<RelationalPersistentProperty> path) {

		Table table = getTable();

		DeleteBuilder.DeleteWhere deleteAll = Delete.builder().from(table);

		if (path == null) {
			return render(deleteAll.build());
		}

		return createDeleteByPathAndCriteria(new PersistentPropertyPathExtension(mappingContext, path), Column::isNotNull);
	}

	@Override
	public String createDeleteByPath(PersistentPropertyPath<RelationalPersistentProperty> path) {
		return createDeleteByPathAndCriteria(new PersistentPropertyPathExtension(mappingContext, path),
				filterColumn -> filterColumn.isEqualTo(getBindMarker(ROOT_ID_PARAMETER)));
	}

	/**
	 * Create a {@code DELETE} query and filter by {@link PersistentPropertyPath} using {@code WHERE} with the {@code IN}
	 * operator.
	 *
	 * @param path must not be {@literal null}.
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	@Override
	public String createDeleteInByPath(PersistentPropertyPath<RelationalPersistentProperty> path) {

		return createDeleteByPathAndCriteria(new PersistentPropertyPathExtension(mappingContext, path),
				filterColumn -> filterColumn.in(getBindMarker(IDS_SQL_PARAMETER)));
	}

	private String createInsertSql(Set<SqlIdentifier> additionalColumns) {

		Table table = getTable();

		Set<SqlIdentifier> columnNamesForInsert = new TreeSet<>(Comparator.comparing(SqlIdentifier::getReference));
		columnNamesForInsert.addAll(columns.getInsertableColumns());
		columnNamesForInsert.addAll(additionalColumns);

		InsertBuilder.InsertIntoColumnsAndValuesWithBuild insert = Insert.builder().into(table);

		for (SqlIdentifier cn : columnNamesForInsert) {
			insert = insert.column(table.column(cn));
		}

		InsertBuilder.InsertValuesWithBuild insertWithValues = null;
		for (SqlIdentifier cn : columnNamesForInsert) {
			insertWithValues = (insertWithValues == null ? insert : insertWithValues).values(getBindMarker(cn));
		}

		return render(insertWithValues == null ? insert.build() : insertWithValues.build());
	}

	private String createUpdateSql() {
		return render(createBaseUpdate().build());
	}

	private String createUpdateWithVersionSql() {

		Update update = createBaseUpdate() //
				.and(getVersionColumn().isEqualTo(SQL.bindMarker(":" + renderReference(VERSION_SQL_PARAMETER)))) //
				.build();

		return render(update);
	}

	private UpdateBuilder.UpdateWhereAndOr createBaseUpdate() {

		Table table = getTable();

		List<AssignValue> assignments = columns.getUpdateableColumns() //
				.stream() //
				.map(columnName -> Assignments.value( //
						table.column(columnName), //
						getBindMarker(columnName))) //
				.collect(Collectors.toList());

		return Update.builder() //
				.table(table) //
				.set(assignments) //
				.where(getIdColumn().isEqualTo(getBindMarker(entity.getIdColumn())));
	}

	private String createDeleteByIdSql() {
		return render(createBaseDeleteById(getTable()).build());
	}

	private String createDeleteByIdInSql() {
		return render(createBaseDeleteByIdIn(getTable()).build());
	}

	private String createDeleteByIdAndVersionSql() {

		Delete delete = createBaseDeleteById(getTable()) //
				.and(getVersionColumn().isEqualTo(SQL.bindMarker(":" + renderReference(VERSION_SQL_PARAMETER)))) //
				.build();

		return render(delete);
	}

	private String createDeleteByIdInAndVersionSql() {

		Delete delete = createBaseDeleteByIdIn(getTable()) //
				.and(getVersionColumn().isEqualTo(SQL.bindMarker(":" + renderReference(VERSION_SQL_PARAMETER)))) //
				.build();

		return render(delete);
	}

	private DeleteBuilder.DeleteWhereAndOr createBaseDeleteById(Table table) {
		return Delete.builder().from(table)
				.where(getIdColumn().isEqualTo(SQL.bindMarker(":" + renderReference(ID_SQL_PARAMETER))));
	}

	private DeleteBuilder.DeleteWhereAndOr createBaseDeleteByIdIn(Table table) {

		return Delete.builder().from(table)
				.where(getIdColumn().in(SQL.bindMarker(":" + renderReference(IDS_SQL_PARAMETER))));
	}

	private String createDeleteByPathAndCriteria(PersistentPropertyPathExtension path,
												 Function<Column, Condition> rootCondition) {

		Table table = Table.create(path.getTableName());

		DeleteBuilder.DeleteWhere builder = Delete.builder() //
				.from(table);
		Delete delete;

		Column filterColumn = table.column(path.getReverseColumnName());

		if (path.getLength() == 1) {

			delete = builder //
					.where(rootCondition.apply(filterColumn)) //
					.build();
		} else {

			Condition condition = getSubselectCondition(path, rootCondition, filterColumn);
			delete = builder.where(condition).build();
		}

		return render(delete);
	}

	private String createDeleteByListSql() {

		Table table = getTable();

		Delete delete = Delete.builder() //
				.from(table) //
				.where(getIdColumn().in(getBindMarker(IDS_SQL_PARAMETER))) //
				.build();

		return render(delete);
	}

	private String render(Insert insert) {
		return this.sqlRenderer.render(insert);
	}

	private String render(Update update) {
		return this.sqlRenderer.render(update);
	}

	private String render(Delete delete) {
		return this.sqlRenderer.render(delete);
	}

	protected Table getTable() {
		return sqlContext.getTable();
	}

	protected Column getIdColumn() {
		return sqlContext.getIdColumn();
	}

	private Column getVersionColumn() {
		return sqlContext.getVersionColumn();
	}

	private String renderReference(SqlIdentifier identifier) {
		return identifier.getReference(renderContext.getIdentifierProcessing());
	}

	protected Column getReverseColumn(PersistentPropertyPathExtension path){
		return sqlContext.getReverseColumn(path);
	}

	protected Column doGetColumn(PersistentPropertyPathExtension path) {
		return sqlContext.getColumn(path);
	}

	protected Table getTable(PersistentPropertyPathExtension path){
		return sqlContext.getTable(path);
	}

	/**
	 * Value object encapsulating column name caches.
	 *
	 * @author Mark Paluch
	 * @author Jens Schauder
	 */
	static class Columns {

		private final MappingContext<RelationalPersistentEntity<?>, RelationalPersistentProperty> mappingContext;
		private final JdbcConverter converter;

		private final List<SqlIdentifier> columnNames = new ArrayList<>();
		private final List<SqlIdentifier> idColumnNames = new ArrayList<>();
		private final List<SqlIdentifier> nonIdColumnNames = new ArrayList<>();
		private final Set<SqlIdentifier> readOnlyColumnNames = new HashSet<>();
		private final Set<SqlIdentifier> insertableColumns;
		private final Set<SqlIdentifier> updateableColumns;

		Columns(RelationalPersistentEntity<?> entity,
				MappingContext<RelationalPersistentEntity<?>, RelationalPersistentProperty> mappingContext,
				JdbcConverter converter) {

			this.mappingContext = mappingContext;
			this.converter = converter;

			populateColumnNameCache(entity, "");

			Set<SqlIdentifier> insertable = new LinkedHashSet<>(nonIdColumnNames);
			insertable.removeAll(readOnlyColumnNames);

			this.insertableColumns = Collections.unmodifiableSet(insertable);

			Set<SqlIdentifier> updateable = new LinkedHashSet<>(columnNames);

			updateable.removeAll(idColumnNames);
			updateable.removeAll(readOnlyColumnNames);

			this.updateableColumns = Collections.unmodifiableSet(updateable);
		}

		private void populateColumnNameCache(RelationalPersistentEntity<?> entity, String prefix) {

			entity.doWithAll(property -> {

				// the referencing column of referenced entity is expected to be on the other side of the relation
				if (!property.isEntity()) {
					initSimpleColumnName(property, prefix);
				} else if (property.isEmbedded()) {
					initEmbeddedColumnNames(property, prefix);
				}
			});
		}

		private void initSimpleColumnName(RelationalPersistentProperty property, String prefix) {

			SqlIdentifier columnName = property.getColumnName().transform(prefix::concat);

			columnNames.add(columnName);

			if (!property.getOwner().isIdProperty(property)) {
				nonIdColumnNames.add(columnName);
			} else {
				idColumnNames.add(columnName);
			}

			if (!property.isWritable()) {
				readOnlyColumnNames.add(columnName);
			}
		}

		private void initEmbeddedColumnNames(RelationalPersistentProperty property, String prefix) {

			String embeddedPrefix = property.getEmbeddedPrefix();

			RelationalPersistentEntity<?> embeddedEntity = mappingContext
					.getRequiredPersistentEntity(converter.getColumnType(property));

			populateColumnNameCache(embeddedEntity, prefix + embeddedPrefix);
		}

		/**
		 * @return Column names that can be used for {@code INSERT}.
		 */
		Set<SqlIdentifier> getInsertableColumns() {
			return insertableColumns;
		}

		/**
		 * @return Column names that can be used for {@code UPDATE}.
		 */
		Set<SqlIdentifier> getUpdateableColumns() {
			return updateableColumns;
		}
	}

}
