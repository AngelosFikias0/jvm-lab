package com.jvmlab.gc;

// EdenDemo — demonstrates how the JVM allocates objects in Eden space
// and how Minor GC fires when Eden fills up.
//
// Eden is the first region of the Young generation. Every new object
// lands here via a bump-pointer allocation — the JVM simply increments
// a pointer, no locking needed. When Eden is full, a Minor GC fires:
// it stops all threads, copies live objects to a Survivor space,
// and resets Eden's pointer to the start. Dead objects cost nothing
// to collect — they are simply abandoned and overwritten next cycle.
//
// Run with: -Xmx64m -Xms64m -Xlog:gc::time
// Watch:    "Pause Young (G1 Evacuation Pause)" lines in GC log.
//           Each line shows heap before/after and pause duration in ms.

public class EdenDemo {

    static long totalAllocatedKB = 0;
    static volatile byte sink; // prevents JIT from eliminating allocations

    public static void main(String[] args) {
        System.out.println("Starting Eden allocation loop.");
        System.out.println("Heap max: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        System.out.println("Watch GC log for 'Pause Young' events.\n");

        long count = 0;

        while (true) {
            // Allocate 10 KB — lands in Eden via TLAB bump pointer.
            // This reference is never stored anywhere, so the object
            // becomes unreachable immediately after this line.
            // The next Minor GC will abandon it at zero cost.
            byte[] garbage = new byte[1024 * 10];
            sink = garbage[0]; // touch so the JIT cannot eliminate the allocation

            // Occasionally allocate something slightly larger to vary pressure
            if (count % 500 == 0) {
                byte[] mediumGarbage = new byte[1024 * 50];
                sink = mediumGarbage[0];
            }

            count++;
            totalAllocatedKB += 10;

            if (count % 1000 == 0) {
                long usedMB = (Runtime.getRuntime().totalMemory()
                        - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                System.out.printf("Iterations: %,d | Approx allocated: %,d MB | Heap used now: %d MB%n",
                        count, totalAllocatedKB / 1024, usedMB);
                try {
                    Thread.sleep(49); // slight pause so output is readable
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
