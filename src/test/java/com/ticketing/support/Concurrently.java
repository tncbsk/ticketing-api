package com.ticketing.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

public final class Concurrently {

    private Concurrently() {
    }

    // starts all tasks at the same moment and returns the exceptions they threw
    public static List<Throwable> run(int threads, IntConsumer task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.submit(() -> {
                try {
                    start.await();
                    task.accept(index);
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Tasks did not finish in time");
        }
        return errors;
    }
}
