package com.chitek.TestUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MockExecutor {

	private final List<ScheduledCommand<?>> scheduledCommands = new LinkedList<ScheduledCommand<?>>();

	public void executeOnce(Runnable command) {
		ScheduledCommand<?> scheduled = new ScheduledCommand<Void>(command, 0, TimeUnit.NANOSECONDS);
		scheduledCommands.add(scheduled);
	}

	public ScheduledFuture<?> executeOnce(Runnable command, long delay, TimeUnit timeUnit) {
		ScheduledCommand<?> scheduled = new ScheduledCommand<Void>(command, delay, timeUnit);
		scheduledCommands.add(scheduled);
		return scheduled;
	}

	/**
	 * @return
	 * 	The count of pending scheduled commands
	 */
	public int getScheduledCount() {
		return scheduledCommands.size();
	}

	/**
	 * Remove all scheduled commands.
	 */
	public void clear() {
		scheduledCommands.clear();
	}
	
	public void runCommand() {
		ScheduledCommand<?> scheduled = scheduledCommands.get(0);
		scheduled.execute();
		scheduledCommands.remove(scheduled);
	}

	public class ScheduledCommand<V> implements ScheduledFuture<V> {

		Runnable command;
		long delay;
		TimeUnit timeUnit;
		private boolean cancelled = false;
		private boolean done = false;

		public ScheduledCommand(Runnable command, long delay, TimeUnit timeUnit) {
			this.command = command;
			this.delay = delay;
			this.timeUnit = timeUnit;
		}

		public void execute() {
			if (cancelled || done) {
				throw new RuntimeException("Execute called for a command that has already executed or is cancelled");
			}

			command.run();
			done = true;
		}

		public long getDelay() {
			return delay;
		}

		public TimeUnit getTimeUnit() {
			return timeUnit;
		}

		@Override
		public long getDelay(TimeUnit unit) {
			throw new UnsupportedOperationException("MockExecutor: ScheduledFuture#getDelay is not supported");
		}

		@Override
		public int compareTo(Delayed o) {
			throw new UnsupportedOperationException("MockExecutor: ScheduledFuture#compareTo is not supported");
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (cancelled || done)
				return false;

			cancelled = true;
			return true;
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public boolean isDone() {
			return done;
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {
			throw new UnsupportedOperationException("MockExecutor: ScheduledFuture#get is not supported");
		}

		@Override
		public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			throw new UnsupportedOperationException("MockExecutor: ScheduledFuture#get is not supported");
		}
	}
}
