package com.jvmlab.gc;

// OOMDemo — simulates a classic memory leak leading to OutOfMemoryError.
//
// A memory leak in Java does not mean memory is lost at the OS level.
// It means objects remain reachable (referenced) when they should not be.
// The GC cannot collect them because they are live from its perspective.
// Old Gen fills up, Full GC runs and recovers nothing, JVM throws OOM.
//
// The leak here is a static field holding a List that never stops
// growing. In production this is usually: a static cache that never
// evicts, a listener never deregistered, a ThreadLocal never cleaned up,
// or a session map that grows with every user request.
//
// Run with: -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom.hprof
// Watch:    GC log shows Full GC firing repeatedly recovering almost nothing.
//           Finally: "OutOfMemoryError: Java heap space"
//           Heap dump written to /tmp/oom.hprof — open in IntelliJ for analysis.

import java.util.ArrayList;
import java.util.List;

public class OOMDemo {

    // Simulates a "cache" that grows forever — the root cause of most leaks
    static final List<byte[]> leakyCache = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Heap max: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        System.out.println("Simulating memory leak — adding to leakyCache with no eviction.\n");

        int iteration = 0;

        try {
            while (true) {
                // Each entry is 1 MB and held permanently in the static list.
                // The GC sees these as live — it cannot collect them.
                leakyCache.add(new byte[1024 * 1024]); // 1 MB
                iteration++;

                if (iteration % 5 == 0) {
                    long used  = (Runtime.getRuntime().totalMemory()
                            - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    long max   = Runtime.getRuntime().maxMemory() / 1024 / 1024;
                    System.out.printf("Cache size: %d MB | Heap used: %d / %d MB%n",
                            leakyCache.size(), used, max);
                    Thread.sleep(100); // slow down so output is readable
                }
            }
        } catch (OutOfMemoryError e) {
            // At this point the JVM has already tried Full GC and recovered nothing.
            // The heap dump (if flag set) is written before this line executes.
            System.err.println("\nOutOfMemoryError after leaking " + leakyCache.size() + " MB");
            System.err.println("If -XX:+HeapDumpOnOutOfMemoryError was set, heap dump is at /tmp/oom.hprof");
            System.err.println("Open it in IntelliJ: File → Open → select oom.hprof");
        }
    }
}
