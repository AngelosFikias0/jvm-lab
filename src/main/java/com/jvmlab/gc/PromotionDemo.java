package com.jvmlab.gc;

// PromotionDemo — demonstrates object promotion from Young Gen to Old Gen.
//
// The JVM tracks how many Minor GCs each object survives via an "age"
// counter stored in the object's mark word (4 bits, max age = 15).
// Objects that survive enough Minor GCs are "promoted" — copied from
// the Survivor space into Old Gen, where they live until a Major GC.
//
// This demo holds a static list (a GC root) of byte arrays so they
// are always reachable and survive every Minor GC. Alongside them,
// it continuously allocates short-lived garbage to trigger Minor GCs.
// Watch the GC logs: after several Minor GCs you'll see "promoted"
// entries and the Old Gen usage climbing in the heap summary.
//
// Run with: -Xmx128m -Xlog:gc*::time -XX:MaxTenuringThreshold=3
// Watch:    "to-space exhausted" or promoted bytes in GC log lines.
//           Old Gen usage rising while Young Gen stays relatively flat.

import java.util.ArrayList;
import java.util.List;

public class PromotionDemo {

    // Static field = GC root. Objects referenced here survive every GC.
    // The JVM increments their age each Minor GC until age > threshold,
    // then promotes them to Old Gen instead of copying to Survivor space.
    static final List<byte[]> longLived = new ArrayList<>();
    static volatile byte sink;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Heap max: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB\n");

        // Phase 1: populate the long-lived list.
        // These objects will be promoted to Old Gen after enough Minor GCs.
        System.out.println("Phase 1: allocating long-lived objects (held by static list)...");
        for (int i = 0; i < 80; i++) {
            longLived.add(new byte[1024 * 10]); // 10 KB each
        }
        System.out.println("Long-lived objects allocated: " + longLived.size()
                + " (~" + (longLived.size() * 10) + " KB)\n");

        // Phase 2: generate garbage to trigger repeated Minor GCs.
        // Each Minor GC increments the age of the longLived objects.
        // Once age exceeds the tenuring threshold (default 15),
        // they are promoted to Old Gen.
        System.out.println("Phase 2: generating garbage to trigger Minor GCs...");
        System.out.println("Watch Old Gen usage climb in GC logs.\n");

        long iterations = 0;
        while (true) {
            // Short-lived garbage — dies immediately after allocation
            byte[] shortLived = new byte[1024 * 5]; // 5 KB
            sink = shortLived[0];
            iterations++;

            if (iterations % 2000 == 0) {
                long used   = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long total  = Runtime.getRuntime().totalMemory();
                System.out.printf("Iterations: %,d | Heap used: %d MB / %d MB committed%n",
                        iterations, used / 1024 / 1024, total / 1024 / 1024);
                Thread.sleep(20);
            }
        }
    }
}