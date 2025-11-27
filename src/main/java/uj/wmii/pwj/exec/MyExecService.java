package uj.wmii.pwj.exec;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MyExecService implements ExecutorService {

    private final Thread workerThread;
    private final BlockingQueue<Runnable> taskQueue;
    private final AtomicBoolean isShutdown;
    private final AtomicBoolean isTerminated;

    private MyExecService() {
        this.taskQueue = new LinkedBlockingQueue<>();
        this.isShutdown = new AtomicBoolean(false);
        this.isTerminated = new AtomicBoolean(false);
        this.workerThread = new Thread(this::runWorker, "MyExecService-Worker");
        this.workerThread.start();
    }

    static MyExecService newInstance() {
        return new MyExecService();
    }

    private void runWorker() {
        while (!isShutdown.get() || !taskQueue.isEmpty()) {
            try {
                Runnable task = taskQueue.take();
                task.run();
            } catch (InterruptedException e) {
                if (isShutdown.get()) {
                    break;
                }
            }
        }
        isTerminated.set(true);
    }

    @Override
    public void shutdown() {
        isShutdown.set(true);
        workerThread.interrupt();
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown.set(true);
        workerThread.interrupt();
        List<Runnable> remainingTasks = taskQueue.stream().collect(Collectors.toList());
        taskQueue.clear();
        return remainingTasks;
    }

    @Override
    public boolean isShutdown() {
        return isShutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return isTerminated.get();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long waitTime = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();

        while (!isTerminated.get() && (System.currentTimeMillis() - startTime) < waitTime) {
            Thread.sleep(10);
        }

        return isTerminated.get();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (isShutdown.get()) {
            throw new RejectedExecutionException("Executor has been shutdown");
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        execute(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return submit(() -> {
            task.run();
            return result;
        });
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(Executors.callable(task, null));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return tasks.stream()
                .map(this::submit)
                .collect(Collectors.toList());
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        List<Future<T>> futures = tasks.stream()
                .map(this::submit)
                .collect(Collectors.toList());

        for (Future<T> future : futures) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;

            try {
                future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException e) {
            }
        }

        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        try {
            return invokeAny(tasks, Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        List<Future<T>> futures = tasks.stream()
                .map(this::submit)
                .collect(Collectors.toList());

        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        while (System.currentTimeMillis() < deadline) {
            for (Future<T> future : futures) {
                if (future.isDone()) {
                    try {
                        return future.get();
                    } catch (ExecutionException e) {
                    }
                }
            }
            Thread.sleep(10);
        }

        throw new TimeoutException("No task completed within timeout");
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown.get()) {
            throw new RejectedExecutionException("Executor has been shutdown");
        }
        taskQueue.offer(command);
    }
}
