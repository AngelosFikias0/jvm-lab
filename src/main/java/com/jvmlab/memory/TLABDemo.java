package com.jvmlab.memory;

// TLABDemo — demonstrates Thread-Local Allocation Buffers (TLABs).
//
// The JVM gives each thread a private slice of Eden called a TLAB.
// Allocation inside a TLAB is a single pointer increment — no locking,
// no CAS, no synchronization between threads at all. This is why
// Java object allocation is nearly as fast as stack allocation in C.
//
// Only when a TLAB is exhausted does the thread synchronize with the
// JVM allocator to claim a new TLAB chunk from Eden. This happens
// rarely compared to the number of allocations.
//
// This demo runs multiple threads allocating heavily in parallel.
// Despite zero explicit synchronization, all allocations are safe
// because each thread works inside its own TLAB region.
//
// Run with: -Xlog:gc+tlab=debug -Xmx128m
// Watch:    per-thread TLAB statistics in GC log showing each thread's
//           allocation rate and how often TLABs are refilled.

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class TLABDemo {

    static final int THREADS        = 4;
    static final int OBJ_PER_THREAD = 200_000;

    // Shared counters — atomic so threads can report without locking alloc path
    static final AtomicLong totalObjects = new AtomicLong(0);
    static final AtomicLong totalBytes   = new AtomicLong(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("TLAB Demo — " + THREADS + " threads allocating "
                + OBJ_PER_THREAD + " objects each");
        System.out.println("Heap max: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB\n");

        CountDownLatch ready  = new CountDownLatch(THREADS);
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            new Thread(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { return; }

                long threadBytes = 0;
                // Each thread allocates into its own TLAB — no coordination needed
                List<Object> local = new ArrayList<>(OBJ_PER_THREAD);
                for (int i = 0; i < OBJ_PER_THREAD; i++) {
                    // Mix of small objects to simulate realistic allocation patterns
                    if (i % 3 == 0) {
                        local.add(new byte[64]);
                        threadBytes += 64;
                    } else if (i % 3 == 1) {
                        local.add(new byte[256]);
                        threadBytes += 256;
                    } else {
                        local.add(new Object());
                        threadBytes += 16;
                    }
                }

                totalObjects.addAndGet(OBJ_PER_THREAD);
                totalBytes.addAndGet(threadBytes);

                System.out.printf("Thread-%d done | objects: %,d | bytes: %,d KB%n",
                        threadId, OBJ_PER_THREAD, threadBytes / 1024);
                finish.countDown();

            }, "allocator-" + t).start();
        }

        ready.await(); // wait for all threads to be ready
        long startMs = System.currentTimeMillis();
        start.countDown(); // release all threads simultaneously
        finish.await();    // wait for all to complete

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("%nAll threads complete.%n");
        System.out.printf("Total objects: %,d%n", totalObjects.get());
        System.out.printf("Total bytes:   %,d KB%n", totalBytes.get() / 1024);
        System.out.printf("Time elapsed:  %d ms%n", elapsedMs);
        System.out.printf("Throughput:    %,d objects/ms%n", totalObjects.get() / Math.max(elapsedMs, 1));
        System.out.println("\nCheck GC log for per-thread TLAB refill counts.");
    }
}