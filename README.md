# jvm-lab

Hands-on Java demos covering JVM internals, memory management, and garbage collection.
Each class is self-contained and runnable with specific JVM flags to observe the
behavior described.

See [CONCEPTS.md](CONCEPTS.md) for theory: JVM architecture, class loading, GC algorithms,
memory areas, and threading model.

## Structure

```
src/main/java/com/jvmlab/
├── gc/
│   ├── EdenDemo.java             - continuous allocation, watch Minor GCs fire
│   ├── PromotionDemo.java        - objects promoted from Young to Old Gen
│   ├── OOMDemo.java              - simulated memory leak → OutOfMemoryError
│   └── GCRootsDemo.java          - what keeps objects alive vs eligible for GC
├── stack/
│   └── StackDemo.java            - stack frames, StackOverflowError, -Xss effect
└── memory/
    ├── TLABDemo.java             - thread-local allocation, no locking between threads
    ├── MetaspaceDemo.java        - dynamic class generation → Metaspace exhaustion
    └── ContainerMemoryDemo.java  - JVM reading cgroup limits vs host RAM
```

## Prerequisites

- Java 21
- Maven 3.9+
- IntelliJ IDEA

## Run configurations

Set these in IntelliJ: **Run → Edit Configurations → + → Application**.
Paste the Main class and VM options for each demo.

To show the VM options field: inside the config dialog click
**Modify options → Add VM options**.

| Demo                | Main class                              | VM options                                                                |
| ------------------- | --------------------------------------- | ------------------------------------------------------------------------- |
| EdenDemo            | `com.jvmlab.gc.EdenDemo`                | `-Xmx64m -Xms64m -Xlog:gc::time`                                          |
| PromotionDemo       | `com.jvmlab.gc.PromotionDemo`           | `-Xmx128m -Xlog:gc*::time -XX:MaxTenuringThreshold=3`                     |
| OOMDemo             | `com.jvmlab.gc.OOMDemo`                 | `-Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom.hprof` |
| GCRootsDemo         | `com.jvmlab.gc.GCRootsDemo`             | `-Xlog:gc::time`                                                          |
| StackDemo           | `com.jvmlab.stack.StackDemo`            | `-Xss256k`                                                                |
| TLABDemo            | `com.jvmlab.memory.TLABDemo`            | `-Xlog:gc+tlab=debug`                                                     |
| MetaspaceDemo       | `com.jvmlab.memory.MetaspaceDemo`       | `-XX:MaxMetaspaceSize=32m`                                                |
| ContainerMemoryDemo | `com.jvmlab.memory.ContainerMemoryDemo` | `-Xmx256m`                                                                |

## What to observe

**EdenDemo** - GC log lines show `Pause Young` firing repeatedly as Eden
fills. Each pause is 1–5 ms. This is the steady state of a running JVM.

**PromotionDemo** - GC logs show objects moving from Young to Old Gen.
Watch `promoted` entries appear after several Minor GCs. Old Gen usage
climbs while Young Gen stays relatively flat.

**OOMDemo** - terminates with `OutOfMemoryError: Java heap space`. Heap
dump written to `/tmp/oom.hprof` (requires `-XX:+HeapDumpOnOutOfMemoryError`).
Open it in IntelliJ via File → Open to see exactly which objects consumed the heap.

**GCRootsDemo** - console output shows which references kept objects
alive and which became eligible for collection after nulling. Run with
`-Xlog:gc::time` to confirm GC fired between the two print blocks.

**StackDemo** - run with `-Xss256k` then change to `-Xss2m` and compare
depth. Same code, different result - only the JVM flag changed. Heavy
recursion experiment shows how local state reduces frames per stack.

**MetaspaceDemo** - terminates with `OutOfMemoryError: Metaspace`. Note
the error message differs from heap OOM - these are separate memory
regions. Heap was fine throughout.

**ContainerMemoryDemo** - run normally to see host values. Then run
inside Docker to see cgroup-aware values:

```bash
docker run -m 512m eclipse-temurin:21 java \
  -XX:MaxRAMPercentage=70.0 \
  -cp /app/target/jvm-lab-1.0.0.jar \
  com.jvmlab.memory.ContainerMemoryDemo
```

**TLABDemo** - each thread allocates into its own private Eden slice with
no synchronization. Despite zero explicit locking, all allocations are safe.
Check GC log for per-thread TLAB refill counts after the run.

## Key JVM flags reference

| Flag                              | Effect                                   |
| --------------------------------- | ---------------------------------------- |
| `-Xmx<size>`                      | Maximum heap size                        |
| `-Xms<size>`                      | Initial committed heap size              |
| `-Xss<size>`                      | Thread stack size                        |
| `-Xlog:gc::time`                  | GC log with timestamps                   |
| `-Xlog:gc*::time`                 | Verbose GC log (all GC subsystems)       |
| `-Xlog:gc+tlab=debug`             | Per-thread TLAB allocation stats         |
| `-XX:MaxMetaspaceSize=<size>`     | Cap Metaspace growth                     |
| `-XX:+HeapDumpOnOutOfMemoryError` | Write heap dump on OOM                   |
| `-XX:HeapDumpPath=<path>`         | Destination for heap dump file           |
| `-XX:MaxRAMPercentage=<n>`        | Heap as percentage of container RAM      |
| `-XX:+UseG1GC`                    | Use G1 garbage collector (default JDK9+) |
| `-XX:+UseZGC`                     | Use ZGC - sub-millisecond pauses         |
| `-XX:MaxGCPauseMillis=<ms>`       | GC pause time target (G1GC)              |

## Key concepts

| Concept                                    | Where demonstrated      |
| ------------------------------------------ | ----------------------- |
| Eden bump-pointer allocation               | EdenDemo                |
| Minor GC - copy live, abandon dead         | EdenDemo, PromotionDemo |
| Object aging and promotion to Old Gen      | PromotionDemo           |
| GC roots - what keeps objects alive        | GCRootsDemo             |
| Memory leak → Full GC → OOM               | OOMDemo                 |
| Thread stack frames and -Xss              | StackDemo               |
| TLAB - lock-free parallel allocation       | TLABDemo                |
| Metaspace - class metadata off-heap        | MetaspaceDemo           |
| cgroup-aware heap sizing                   | ContainerMemoryDemo     |

## External resources

- [Java 21 GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/) - authoritative reference for G1, ZGC, and all tuning flags
- [Alexey Shipilev's blog](https://shipilev.net/) - best JVM internals writing available; covers GC, memory model, JIT in depth
- [Eclipse Memory Analyzer (MAT)](https://eclipse.dev/mat/) - analyze the `.hprof` heap dumps produced by OOMDemo
- [async-profiler](https://github.com/async-profiler/async-profiler) - CPU and allocation profiling with flame graphs; pairs well with EdenDemo and TLABDemo
