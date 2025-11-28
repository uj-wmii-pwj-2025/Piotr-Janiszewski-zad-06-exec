package uj.wmii.pwj.exec;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Collectors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.lang.NullPointerException;
import java.lang.Throwable;

public class MyExecService implements ExecutorService {

	private final Object lock = new Object();

	private class ExecutionUnit<T> {
		Callable<T> callable = null;
		Runnable runnable = null;
		T returnValue = null;
		CompletableFuture<T> future = new CompletableFuture<>();
	}

	private Queue<ExecutionUnit<?>> tasks = new LinkedList<>();

	private class MyExecutor implements Runnable {
		private MyExecService service;

		MyExecutor(MyExecService service) {
			this.service = service;
		}

		@Override
		public void run() {
			ExecutionUnit<?> task;
			do {
				synchronized(lock) {
					task = tasks.poll();
				}

				if (task != null) {
					executeTask(task);
				}
			} while(task != null && !Thread.currentThread().isInterrupted());
		}

		static protected <T> void executeTask(ExecutionUnit<?> task) {
			try {
				if (task.callable != null) {
					ExecutionUnit<T> typedTask = (ExecutionUnit<T>) task;
					T result = typedTask.callable.call();
					typedTask.future.complete(result);
				}
				else if (task.runnable != null) {
					ExecutionUnit<T> typedTask = (ExecutionUnit<T>) task;
					typedTask.runnable.run();
					typedTask.future.complete(typedTask.returnValue);
				}
				else
					throw new Exception("A `Task` has no executable code attached.");
			}
			catch (Exception exception) {
				task.future.completeExceptionally(exception);
			}
		}
	}

	Thread mainThread = null;

	private boolean shutdown = false;

	private void botherExecutor() {
		synchronized (lock) {
			if (mainThread == null) {
				mainThread = new Thread(new MyExecutor(this));
				mainThread.start();
			}
			else if (mainThread.getState() == Thread.State.TERMINATED) {
				mainThread = new Thread(new MyExecutor(this));
				mainThread.start();
			}
		}
	}
///

	static MyExecService newInstance() {
		return new MyExecService();
	}

	@Override
	public void shutdown() {
		synchronized (lock) {
			shutdown = true;
		}
	}

	@Override
	public List<Runnable> shutdownNow() {
		List<Runnable> remainingTasks = new ArrayList<>();

		synchronized (lock) {
			shutdown = true;

			for (ExecutionUnit<?> task : tasks) {
				/*if (task.callable != null) {
					remainingTasks.add(() -> {callableWrapper(task);});
				}
				else if (task.runnable != null) {
					remaingTasks.add(() -> {runnableWrapper(task);});
				}*/
				remainingTasks.add(() -> {MyExecutor.executeTask(task);});
			}
			tasks.clear();
		}

		return remainingTasks;
	}

	/*
	private <T> void callableWrapper(ExecutionUnit<T> task) {
		try {
			task.future.complete(task.callable.call());
		}
		catch (Exception exception) {
			task.future.completeExceptionally(exception);
		}
	}

	private <T> void runnableWrapper(ExecutionUnit<T> task) {
		try {
			task.runnable.run();
			task.future.complete(task.returnValue);
		}
		catch (Exception exception) {
			task.future.completeExceptionally(exception);
		}
	}
	*/

	@Override
	public boolean isShutdown() {
		synchronized (lock) {
			return shutdown;
		}
	}

	@Override
	public boolean isTerminated() {
		synchronized (lock) {
			if (!tasks.isEmpty())
				return false;
			if (mainThread != null && mainThread.isAlive())
				return false;

			return shutdown;
		}
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		long beginning = System.nanoTime();
		long timeLeft = unit.toNanos(timeout);
		long deadline = beginning + timeLeft;

		do {
			if (mainThread != null)
				TimeUnit.NANOSECONDS.timedJoin(mainThread, timeLeft);
			timeLeft = deadline - System.nanoTime();
			deadline = beginning + timeLeft;
		} while(timeLeft > 0 && !isTerminated());

		return isTerminated();
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) throws RejectedExecutionException, NullPointerException{
		if (task == null)
			throw new NullPointerException();

		if (isShutdown())
			throw new RejectedExecutionException();

		ExecutionUnit<T> newTask = new ExecutionUnit();
		newTask.callable = task;
		synchronized (lock) {
			tasks.add(newTask);
			botherExecutor();
		}

		return newTask.future;
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) throws RejectedExecutionException, NullPointerException{
		if (task == null)
			throw new NullPointerException();
		if (isShutdown())
			throw new RejectedExecutionException();

		ExecutionUnit<T> newTask = new ExecutionUnit<>();
		newTask.runnable = task;
		newTask.returnValue = result;
		synchronized (lock) {
			tasks.add(newTask);
			botherExecutor();
		}

		return newTask.future;
	}

	@Override
	public Future<?> submit(Runnable task) throws RejectedExecutionException, NullPointerException{
		if (task == null)
			throw new NullPointerException();

		if (isShutdown())
			throw new RejectedExecutionException();

		ExecutionUnit<Void> newTask = new ExecutionUnit<>();
		newTask.runnable = task;
		synchronized (lock) {
			tasks.add(newTask);
			botherExecutor();
		}

		return newTask.future;
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException, NullPointerException {
		if (tasks == null || tasks.contains(null))
			throw new NullPointerException();

		List<Future<T>> resultantFutures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			resultantFutures.add(submit(task));
		}

		for (Future<T> future : resultantFutures) {
			try {
				future.get();
			}
			catch (Exception exception) {
				synchronized (lock) {
					for (Future<T> futureToRemove : resultantFutures)
						tasks.remove(futureToRemove);
				}

				throw new InterruptedException();
			}
		}


		return resultantFutures;
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		if (tasks == null || tasks.contains(null) || unit == null)
			throw new NullPointerException();

		List<Future<T>> resultantFutures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			resultantFutures.add(submit(task));
		}

		long timeoutInNanoseconds = unit.toNanos(timeout);
		long deadline = System.nanoTime() + timeoutInNanoseconds;

		for (Future<T> future : resultantFutures) {
			long remainingTime = deadline - System.nanoTime();
			if (remainingTime <= 0)
				break;
			try {
				future.get(remainingTime, TimeUnit.NANOSECONDS);
			}
			catch (TimeoutException exception) {}
			catch (Exception exception) {
				throw new InterruptedException();
			}
		}

		return resultantFutures;
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException, IllegalArgumentException {
		if (tasks == null || tasks.contains(null))
			throw new NullPointerException();

		if (tasks.size() == 0)
			throw new IllegalArgumentException();

		List<Future<T>> resultantFutures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			resultantFutures.add(submit(task));
		}

		while (!resultantFutures.isEmpty()) {
			for (int i = 0; i < resultantFutures.size(); i++) {
				Future<T> currentFuture = resultantFutures.get(i);

				if (!currentFuture.isDone())
					continue;
				else if (currentFuture.state() == Future.State.SUCCESS) {
					synchronized (lock) {
						for (Future<T> future : resultantFutures)
							tasks.remove(future);
					}
					return currentFuture.get();
				}
				else if (currentFuture.isDone()) {
					resultantFutures.remove(i);
					i--;
				}
			}
		}

		throw new ExecutionException(new Throwable("No successful task"));
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (tasks == null || tasks.contains(null) || unit == null)
			throw new NullPointerException();

		if (tasks.size() == 0)
			throw new IllegalArgumentException();

		List<Future<T>> resultantFutures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			resultantFutures.add(submit(task));
		}

		timeout = unit.toNanos(timeout);

		while (!resultantFutures.isEmpty()) {
			for (int i = 0; i < resultantFutures.size(); i++) {
				Future<T> currentFuture = resultantFutures.get(i);

				long beginning = System.nanoTime();
				if (!currentFuture.isDone())
					continue;
				else if (currentFuture.state() == Future.State.SUCCESS) {
					synchronized (lock) {
						for (Future<T> future : resultantFutures)
							tasks.remove(future);
					}
					return currentFuture.get();
				}
				else if (currentFuture.isDone()) {
					resultantFutures.remove(i);
					i--;
				}
				long end = System.nanoTime();
				long duration = end - beginning;
				timeout -= duration;
				if (timeout <= 0)
					throw new TimeoutException();
			}
		}

		throw new ExecutionException(new Throwable("No successful task"));
	}

	@Override
	public void execute(Runnable command) throws NullPointerException {
		if (command == null)
			throw new NullPointerException();

		submit(command);
	}
}
