package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(10);
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(10);
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(10);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
        s.shutdown();
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
        s.shutdown();
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertThrows(
                RejectedExecutionException.class,
                () -> s.submit(new TestRunnable()));
    }

    @Test
    void testShutdownNow() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r1 = new TestRunnable();
        TestRunnable r2 = new TestRunnable();
        s.execute(r1);
        s.execute(r2);
        List<Runnable> notExecuted = s.shutdownNow();
        assertTrue(notExecuted.size() >= 1);
        assertTrue(s.isShutdown());
    }

    @Test
    void testInvokeAll() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = List.of(
                new StringCallable("A", 5),
                new StringCallable("B", 5),
                new StringCallable("C", 5)
        );

        List<Future<String>> futures = s.invokeAll(tasks);
        doSleep(20);

        assertEquals(3, futures.size());
        assertTrue(futures.get(0).isDone());
        assertEquals("A", futures.get(0).get());
        assertEquals("B", futures.get(1).get());
        assertEquals("C", futures.get(2).get());

        s.shutdown();
    }

    @Test
    void testInvokeAny() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = List.of(
                new StringCallable("First", 5),
                new StringCallable("Second", 10),
                new StringCallable("Third", 15)
        );

        String result = s.invokeAny(tasks);
        assertNotNull(result);
        s.shutdown();
    }

    @Test
    void testIsTerminated() throws Exception {
        MyExecService s = MyExecService.newInstance();
        assertFalse(s.isTerminated());
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertTrue(s.awaitTermination(100, TimeUnit.MILLISECONDS));
        assertTrue(s.isTerminated());
    }

    @Test
    void testMultipleTasksExecutionOrder() throws Exception {
        MyExecService s = MyExecService.newInstance();
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            s.execute(() -> {
                doSleep(5);
                counter.incrementAndGet();
            });
        }

        doSleep(50);
        assertEquals(5, counter.get());
        s.shutdown();
    }

    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class StringCallable implements Callable<String> {

    private final String result;
    private final int milis;

    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }

    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}

class TestRunnable implements Runnable {

    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }
}
