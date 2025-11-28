package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class MyExecServiceTest {
	@Test
	void testShutdownNow() {
		MyExecService service = MyExecService.newInstance();
		UnendingRunnable unending = new UnendingRunnable();
		service.submit(unending);

		doSleep(10);

		TestRunnable a = new TestRunnable();
		TestRunnable b = new TestRunnable();

		service.submit(a);
		service.submit(b);

		doSleep(10);

		assertTrue(!a.wasRun, "Tasks added later executed too soon");
		assertTrue(!b.wasRun, "Tasks added later executed too soon");

		List<Runnable> awaitingExecution = service.shutdownNow();

		unending.end();

		assertTrue(!a.wasRun, "Task begun and finished after shutdown");
		assertTrue(!b.wasRun, "Task begun and finished after shutdown");

		for (Runnable task : awaitingExecution)
			task.run();
		
		assertTrue(a.wasRun, "Unfinished task has not been returned by `shutdownNow`");
		assertTrue(b.wasRun, "Unfinished task has not been returned by `shutdownNow`");
		assertTrue(awaitingExecution.size() >= 2, "Too many tasks returned by `shutdownNow`");
	}

	@Test
	void testIsShutdown() {
		MyExecService service = MyExecService.newInstance();

		assertTrue(!service.isShutdown(), "`isShutdown() returns true before shutdown");

		TestRunnable a = new TestRunnable();
		service.submit(a);

		doSleep(10);

		assertTrue(!service.isShutdown(), "`isShutdown() returns true before shutdown");

		service.shutdown();

		doSleep(10);

		assertTrue(service.isShutdown(), "`isShutdown() doesn't return true after shutdown");
	}

	@Test
	void testIsTerminated() {
		MyExecService service = MyExecService.newInstance();
			
		assertTrue(!service.isTerminated(), "`isTerminated()` returns true before shutdown");

		TestRunnable a = new TestRunnable();
		service.submit(a);

		assertTrue(!service.isTerminated(), "`isTerminated()` returns true before shutdown");

		UnendingRunnable unending = new UnendingRunnable();
		UnendingRunnable secondUnending = new UnendingRunnable();
		service.submit(unending);
		service.submit(secondUnending);

		doSleep(10);

		assertTrue(!service.isTerminated(), "`isTerminated()` returns true before shutdown");

		service.shutdown();

		assertTrue(!service.isTerminated(), "`isTerminated()` returns true after shutdown, but before completing all tasks");

		unending.end();

		assertTrue(!service.isTerminated(), "`isTerminated()` returns true after shutdown, but before completing all tasks");

		secondUnending.end();

		doSleep(10);

		assertTrue(service.isTerminated(), "`isTerminated()` returns false after shutdown and completing all tasks");

	}

	@Test
	void testAwaitTermination() {
		try {
			MyExecService service = MyExecService.newInstance();

			assertTrue(!service.awaitTermination(1, TimeUnit.MILLISECONDS), "`AwaitTermination()` returns true before shutdown");
				
			TestRunnable a = new TestRunnable();
			service.submit(a);

			long beginning = System.nanoTime();
			assertTrue(!service.awaitTermination(1, TimeUnit.MILLISECONDS), "`AwaitTermination()` returns true before shutdown");
			long end = System.nanoTime();
			long duration = end - beginning;

			assertTrue(duration > 1000, "`awaitTermination()` didn't wait for a shutdown");

			UnendingRunnable unending = new UnendingRunnable();
			service.submit(unending);

			beginning = System.nanoTime();
			assertTrue(!service.awaitTermination(1, TimeUnit.MILLISECONDS), "`AwaitTermination()` returns true before shutdown");
			end = System.nanoTime();
			duration = end - beginning;

			assertTrue(duration > 1000, "`awaitTermination()` didn't wait for a shutdown");

			SleepingRunnable sleepyHead = new SleepingRunnable();
			service.submit(sleepyHead);

			service.shutdown();

			unending.end();
			
			assertTrue(!service.awaitTermination(1, TimeUnit.MILLISECONDS), "`AwaitTermination()` returns true before the service finishes all the tasks");
			assertTrue(service.awaitTermination(10, TimeUnit.MILLISECONDS), "`AwaitTermination() returns false after the service finished all the tasks and was shutdown`");
		}
		catch (Exception exception) {
			exception.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	void testInvokeAll() {
		String first = "我要自杀";
		String second = "清杀了我把";
		String third = "我不想活";

		Set<String> strings = new HashSet();
		strings.add(first);
		strings.add(second);
		strings.add(third);

		List<Callable<String>> tasks = new ArrayList<>();
		tasks.add(new StringCallable(first, 3));
		tasks.add(new StringCallable(second, 3));
		tasks.add(new StringCallable(third, 3));

		MyExecService service = MyExecService.newInstance();
		
		try {
			List<Future<String>> futures = service.invokeAll(tasks);
			for (Future<String> future : futures) {
				assertTrue(future.isDone(), "Task not done after invokeAll finished");
				assertTrue(strings.remove(future.get()), "Incorrect result after invokeAll finished");
			}
		}
		catch (Exception exception) {
			exception.printStackTrace();
			fail("Unexcpected exception");
		}

	}

	@Test
	void testInvokeAny() {
		String first = "我要自杀";
		String second = "清杀了我把";
		String third = "我不想活";

		Set<String> strings = new HashSet();
		strings.add(first);
		strings.add(second);
		strings.add(third);

		List<TestCallable> tasks = new ArrayList<>();
		tasks.add(new TestCallable(first, 3));
		tasks.add(new TestCallable(second, 3));
		tasks.add(new TestCallable(third, 3));

		MyExecService service = MyExecService.newInstance();
		
		try {
			String result = service.invokeAny(tasks);
			assertTrue(strings.remove(result), "Incorrect result after invokeAny finished");

			service.shutdown();
			assertTrue(service.awaitTermination(10, TimeUnit.MILLISECONDS), "Service can't shutdown after invokeAny has finished");
		}
		catch (Exception exception) {
			exception.printStackTrace();
			fail("Unexcpected exception encountered");
		}

		assertTrue(	tasks.get(0).wasRun &
				tasks.get(1).wasRun &
				tasks.get(2).wasRun, "All tasks were finished after invokeAny had returned a result");
	}

	static void doSleep(int milis) {
		try {
			Thread.sleep(milis);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}

class TestCallable implements Callable<String> {
	public boolean wasRun = false;

	private final String result;
	private final int milis;

	TestCallable(String result, int milis) {
		this.result = result;
		this.milis = milis;
	}

	@Override
	public String call() throws Exception {
		ExecServiceTest.doSleep(milis);
		wasRun = true;
		return result;
	}
}

class UnendingRunnable implements Runnable {
	private Boolean end = false;

	private Object lock = new Object();

	@Override
	public void run() {
		boolean buffer = true;
		while (buffer) {
			synchronized(lock) {
				buffer = !end;
			}
		}
	}

	public void end() {
		synchronized(lock) {
			end = true;
		}
	}

	public boolean getEnd() {
		synchronized(lock) {
			return end;
		}
	}
}

class SleepingRunnable implements Runnable {
	public boolean wasRun = false;

	@Override
	public void run() {
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		wasRun = true;
	}
}
