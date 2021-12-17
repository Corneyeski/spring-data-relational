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

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.convert.Identifier;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Set;

public interface SqlGenerator {
	/**
	 * Returns a query for selecting all simple properties of an entity, including those for one-to-one relationships.
	 * Results are filtered using an {@code IN}-clause on the id column.
	 *
	 * @return a SQL statement. Guaranteed to be not {@code null}.
	 */
	String getFindAllInList();

	/**
	 * Returns a query for selecting all simple properties of an entity, including those for one-to-one relationships.
	 *
	 * @return a SQL statement. Guaranteed to be not {@code null}.
	 */
	String getFindAll();

	/**
	 * Create a {@code INSERT INTO … (…) VALUES(…)} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getInsert(Set<SqlIdentifier> additionalColumns);

	/**
	 * Create a {@code UPDATE … SET …} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getUpdate();

	/**
	 * Create a {@code UPDATE … SET … WHERE ID = :id and VERSION_COLUMN = :___oldOptimisticLockingVersion } statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getUpdateWithVersion();

	/**
	 * Create a {@code DELETE FROM … WHERE :id = …} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getDeleteById();

	String getDeleteByIdIn();

	/**
	 * Create a {@code DELETE FROM … WHERE :id = … and :___oldOptimisticLockingVersion = ...} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getDeleteByIdAndVersion();

	/**
	 * Create a {@code DELETE} query and optionally filter by {@link PersistentPropertyPath}.
	 *
	 * @param path can be {@literal null}.
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String createDeleteAllSql(@Nullable PersistentPropertyPath<RelationalPersistentProperty> path);

	/**
	 * Create a {@code DELETE} query and filter by {@link PersistentPropertyPath}.
	 *
	 * @param path must not be {@literal null}.
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String createDeleteByPath(PersistentPropertyPath<RelationalPersistentProperty> path);

	/**
	 * Create a {@code DELETE} query and filter by {@link PersistentPropertyPath} using {@code WHERE} with the {@code IN}
	 * operator.
	 *
	 * @param path must not be {@literal null}.
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String createDeleteInByPath(PersistentPropertyPath<RelationalPersistentProperty> path);
	/**
	 * Returns a query for selecting all simple properties of an entity, including those for one-to-one relationships,
	 * sorted by the given parameter.
	 *
	 * @return a SQL statement. Guaranteed to be not {@code null}.
	 */
	String getFindAll(Sort sort);

	/**
	 * Returns a query for selecting all simple properties of an entity, including those for one-to-one relationships,
	 * paged and sorted by the given parameter.
	 *
	 * @return a SQL statement. Guaranteed to be not {@code null}.
	 */
	String getFindAll(Pageable pageable);

	/**
	 * Returns a query for selecting all simple properties of an entity, including those for one-to-one relationships.
	 * Results are limited to those rows referencing some other entity using the column specified by
	 * {@literal columnName}. This is used to select values for a complex property ({@link Set}, {@link Map} ...) based on
	 * a referencing entity.
	 *
	 * @param parentIdentifier name of the column of the FK back to the referencing entity.
	 * @param keyColumn if the property is of type {@link Map} this column contains the map key.
	 * @param ordered whether the SQL statement should include an ORDER BY for the keyColumn. If this is {@code true}, the
	 *          keyColumn must not be {@code null}.
	 * @return a SQL String.
	 */
	String getFindAllByProperty(Identifier parentIdentifier, @Nullable SqlIdentifier keyColumn, boolean ordered);

	/**
	 * Create a {@code SELECT COUNT(id) FROM … WHERE :id = …} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getExists();

	/**
	 * Create a {@code SELECT … FROM … WHERE :id = …} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getFindOne();

	/**
	 * Create a {@code SELECT count(id) FROM … WHERE :id = … (LOCK CLAUSE)} statement.
	 *
	 * @param lockMode Lock clause mode.
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getAcquireLockById(LockMode lockMode);

	/**
	 * Create a {@code SELECT count(id) FROM … (LOCK CLAUSE)} statement.
	 *
	 * @param lockMode Lock clause mode.
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getAcquireLockAll(LockMode lockMode);

	/**
	 * Create a {@code SELECT COUNT(*) FROM …} statement.
	 *
	 * @return the statement as a {@link String}. Guaranteed to be not {@literal null}.
	 */
	String getCount();
}
