package org.springframework.data.relational.core.conversion;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

import lombok.Value;

/**
 * Unit tests for {@link DeleteBatchingAggregateChange}.
 *
 * @author Chirag Tailor
 */
class DeleteBatchingAggregateChangeTest {

	RelationalMappingContext context = new RelationalMappingContext();

	@Test
	void yieldsDeleteActions() {

		Root root = new Root(1L, null);
		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(root);
		DbAction.Delete<Intermediate> intermediateDelete = new DbAction.Delete<>(1L,
				context.getPersistentPropertyPath("intermediate", Root.class));
		aggregateChange.addAction(intermediateDelete);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		assertThat(extractActions(change)).containsExactly(intermediateDelete);
	}

	@Test
	void yieldsNestedDeleteActionsInTreeOrderFromLeavesToRoot() {

		Root root = new Root(2L, null);
		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(root);
		DbAction.Delete<Intermediate> intermediateDelete = new DbAction.Delete<>(1L,
				context.getPersistentPropertyPath("intermediate", Root.class));
		aggregateChange.addAction(intermediateDelete);
		DbAction.Delete<?> leafDelete = new DbAction.Delete<>(1L,
				context.getPersistentPropertyPath("intermediate.leaf", Root.class));
		aggregateChange.addAction(leafDelete);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		List<DbAction<?>> actions = extractActions(change);
		assertThat(actions).containsExactly(leafDelete, intermediateDelete);
	}

	@Test
	void yieldsDeleteActionsAsBatchDeletes_groupedByPath_whenGroupContainsMultipleDeletes() {

		Root root = new Root(1L, null);
		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(root);
		DbAction.Delete<Intermediate> intermediateDelete1 = new DbAction.Delete<>(1L,
				context.getPersistentPropertyPath("intermediate", Root.class));
		DbAction.Delete<Intermediate> intermediateDelete2 = new DbAction.Delete<>(2L,
				context.getPersistentPropertyPath("intermediate", Root.class));
		aggregateChange.addAction(intermediateDelete1);
		aggregateChange.addAction(intermediateDelete2);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		List<DbAction<?>> actions = extractActions(change);
		assertThat(actions).extracting(DbAction::getClass, DbAction::getEntityType) //
				.containsExactly(Tuple.tuple(DbAction.BatchDelete.class, Intermediate.class));
		assertThat(getBatchWithValueAction(actions, Intermediate.class, DbAction.BatchDelete.class).getActions())
				.containsExactly(intermediateDelete1, intermediateDelete2);
	}

	@Test
	void yieldsDeleteRootActions() {

		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(new Root(null, null));
		DbAction.DeleteRoot<Root> deleteRoot = new DbAction.DeleteRoot<>(1L, Root.class, null);
		aggregateChange.addAction(deleteRoot);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		assertThat(extractActions(change)).containsExactly(deleteRoot);
	}

	@Test
	void yieldsDeleteRootActionsAfterDeleteActions() {

		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(new Root(null, null));
		DbAction.DeleteRoot<Root> deleteRoot = new DbAction.DeleteRoot<>(1L, Root.class, null);
		aggregateChange.addAction(deleteRoot);
		DbAction.Delete<?> intermediateDelete = new DbAction.Delete<>(1L,
				context.getPersistentPropertyPath("intermediate", Root.class));
		aggregateChange.addAction(intermediateDelete);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		assertThat(extractActions(change)).containsExactly(intermediateDelete, deleteRoot);
	}

	@Test
	void yieldsLockRootActions() {

		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(new Root(null, null));
		DbAction.AcquireLockRoot<Root> lockRootAction = new DbAction.AcquireLockRoot<>(1L, Root.class);
		aggregateChange.addAction(lockRootAction);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		assertThat(extractActions(change)).containsExactly(lockRootAction);
	}

	@Test
	void yieldsLockRootActionsBeforeDeleteActions() {

		DeleteAggregateChange<Root> aggregateChange = MutableAggregateChange.forDelete(new Root(null, null));
		DbAction.Delete<?> intermediateDelete = new DbAction.Delete<>(1L,
				context.getPersistentPropertyPath("intermediate", Root.class));
		aggregateChange.addAction(intermediateDelete);
		DbAction.AcquireLockRoot<Root> lockRootAction = new DbAction.AcquireLockRoot<>(1L, Root.class);
		aggregateChange.addAction(lockRootAction);

		BatchingAggregateChange<Root, DeleteAggregateChange<Root>> change = BatchingAggregateChange.forDelete(Root.class);
		change.add(aggregateChange);

		assertThat(extractActions(change)).containsExactly(lockRootAction, intermediateDelete);
	}

	private <T> List<DbAction<?>> extractActions(BatchingAggregateChange<T, ? extends MutableAggregateChange<T>> change) {

		List<DbAction<?>> actions = new ArrayList<>();
		change.forEachAction(actions::add);
		return actions;
	}

	private <T, A> DbAction.BatchWithValue<T, DbAction<T>, Object> getBatchWithValueAction(List<DbAction<?>> actions,
			Class<T> entityType, Class<A> batchActionType) {

		return getBatchWithValueActions(actions, entityType, batchActionType).stream().findFirst()
				.orElseThrow(() -> new RuntimeException("No BatchWithValue action found!"));
	}

	private <T, A> DbAction.BatchWithValue<T, DbAction<T>, Object> getBatchWithValueAction(List<DbAction<?>> actions,
			Class<T> entityType, Class<A> batchActionType, Object batchValue) {

		return getBatchWithValueActions(actions, entityType, batchActionType).stream()
				.filter(batchWithValue -> batchWithValue.getBatchValue() == batchValue).findFirst().orElseThrow(
						() -> new RuntimeException(String.format("No BatchWithValue with batch value '%s' found!", batchValue)));
	}

	@SuppressWarnings("unchecked")
	private <T, A> List<DbAction.BatchWithValue<T, DbAction<T>, Object>> getBatchWithValueActions(
			List<DbAction<?>> actions, Class<T> entityType, Class<A> batchActionType) {

		return actions.stream() //
				.filter(dbAction -> dbAction.getClass().equals(batchActionType)) //
				.filter(dbAction -> dbAction.getEntityType().equals(entityType)) //
				.map(dbAction -> (DbAction.BatchWithValue<T, DbAction<T>, Object>) dbAction).collect(Collectors.toList());
	}

	@Value
	static class Root {
		@Id Long id;
		Intermediate intermediate;
	}

	@Value
	static class Intermediate {
		@Id Long id;
		String name;
		Leaf leaf;
	}

	@Value
	static class Leaf {
		@Id Long id;
		String name;
	}
}
