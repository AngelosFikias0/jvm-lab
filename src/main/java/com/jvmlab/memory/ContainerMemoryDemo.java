package com.jvmlab.memory;

// ContainerMemoryDemo — shows how the JVM reads memory and CPU limits
// from cgroup constraints rather than host hardware.
//
// Before JDK 8u191, the JVM read /proc/meminfo and /proc/cpuinfo —
// which return host values regardless of container limits. A JVM inside
// a 512 MB container on a 64 GB host would see 64 GB and set its default
// heap to 16 GB, immediately triggering an OOMKill from the cgroup.
//
// JDK 10+ (backported to 8u191) introduced UseContainerSupport (on by
// default). The JVM now reads /sys/fs/cgroup/ for memory limits and
// calculates effective CPU count from CPU quota/period. This means the
// JVM correctly sizes its heap and thread pools for the container.
//
// However: the default MaxRAMPercentage is 25%. On a 512 MB container
// that gives a 128 MB heap — usually too small for Spring Boot.
// Always set -XX:MaxRAMPercentage=60.0 to 75.0 explicitly.
// Never set -Xmx equal to the container limit — the JVM needs headroom
// for Metaspace, thread stacks, code cache, and direct buffers.
//
// Run with: -Xmx256m
// Then run inside Docker: docker run -m 512m <image> java ContainerMemoryDemo
// Compare the numbers — cgroup awareness in action.

public class ContainerMemoryDemo {

    public static void main(String[] args) {
        Runtime rt = Runtime.getRuntime();

        long maxHeapMB   = rt.maxMemory()   / 1024 / 1024;
        long totalHeapMB = rt.totalMemory() / 1024 / 1024;
        long freeHeapMB  = rt.freeMemory()  / 1024 / 1024;
        long usedHeapMB  = totalHeapMB - freeHeapMB;
        int  cpus        = rt.availableProcessors();

        System.out.println("=== JVM Memory Report ===\n");

        System.out.println("Heap:");
        System.out.printf("  Max     (-Xmx or MaxRAMPercentage): %d MB%n", maxHeapMB);
        System.out.printf("  Total   (committed from OS):        %d MB%n", totalHeapMB);
        System.out.printf("  Used    (live objects):             %d MB%n", usedHeapMB);
        System.out.printf("  Free    (committed but unused):     %d MB%n", freeHeapMB);

        System.out.println("\nCPU:");
        System.out.printf("  Available processors (cgroup-aware): %d%n", cpus);

        System.out.println("\nNon-heap (estimated, not directly readable via Runtime):");
        System.out.println("  Metaspace:     typically 50-200 MB (grows with classes loaded)");
        System.out.println("  Thread stacks: " + cpus * 2 + " threads × 512 KB = ~"
                + (cpus * 2 * 512 / 1024) + " MB estimate");
        System.out.println("  Code cache:    up to 240 MB (JIT compiled code)");
        System.out.println("  Direct bufs:   depends on Netty/NIO usage");

        System.out.println("\nTotal JVM process RSS estimate:");
        long estimatedRSS = maxHeapMB + 300 + (cpus * 2 * 512 / 1024) + 240;
        System.out.printf("  %d MB heap + ~%d MB overhead = ~%d MB total%n",
                maxHeapMB, estimatedRSS - maxHeapMB, estimatedRSS);
        System.out.println("  → Set container limit to at least 1.5× your -Xmx");

        System.out.println("\nRecommended flags for containers:");
        System.out.println("  -XX:MaxRAMPercentage=70.0     (heap = 70% of container limit)");
        System.out.println("  -XX:+UseG1GC                  (balanced GC for most workloads)");
        System.out.println("  -XX:MaxGCPauseMillis=200      (GC pause target)");
        System.out.println("  -XX:+HeapDumpOnOutOfMemoryError");
        System.out.println("  -XX:HeapDumpPath=/tmp/heap.hprof");
    }
}
