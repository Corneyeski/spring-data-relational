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

import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

class CachingResultSet {

	public static final String UNSUPPORTED = "CachingResultSet does not implement this method. It is not intended as a full stand in of a ResultSet";
	private final ResultSet delegate;
	private Cache cache;

	CachingResultSet(ResultSet delegate) {

		this.delegate = delegate;
	}

	public boolean next() throws SQLException {

		if (isPeeking()) {

			final boolean next = cache.next;
			cache = null;
			return next;
		}
		return delegate.next();
	}

	@Nullable
	public Object getObject(String columnLabel) throws SQLException {

		if (isPeeking()) {
			return cache.values.get(columnLabel);
		}
		return delegate.getObject(columnLabel);
	}

	@Nullable
	Object peek(String columnLabel) throws SQLException {

		if (!isPeeking()) {
			createCache();
		}

		if (!cache.next) {
			return null;
		}
		return delegate.getObject(columnLabel);
	}

	private void createCache() throws SQLException {
		cache = new Cache();

		int columnCount = delegate.getMetaData().getColumnCount();
		for (int i = 1; i <= columnCount; i++) {
			cache.add(delegate.getMetaData().getColumnLabel(i), delegate.getObject(i));
		}

		cache.next = delegate.next();

	}

	private boolean isPeeking() {
		return cache != null;
	}

	private static class Cache {

		boolean next;
		Map<String, Object> values = new HashMap<>();

		void add(String columnName, Object value) {
			values.put(columnName, value);
		}
	}
}
