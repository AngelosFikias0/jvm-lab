# Java

- C needed to be compiled for each specific architecture
- Sun Microsystems wanted a language that could run anywhere
- Initially built for set-top boxes/cable TVs, then adopted for web apps
- Compiles to bytecode → JVM → machine code (WORA: Write Once, Run Anywhere)
- Key features: GC, strong static typing, OOP, multithreading, security model
- Became dominant in enterprise backends in 1995

---

## Toolchain: JDK → JRE → JVM

```text
JDK (Java Development Kit)        ← what developers install
  ├── javac                        # compiler: .java source → .class bytecode
  ├── jar, javap, jshell, jdb      # packaging, disassembler, REPL, debugger
  └── JRE (Java Runtime Environment)   ← the runtime (bundled inside JDK since Java 11)
        ├── Standard libraries     # java.lang, java.util, java.io, etc.
        └── JVM                    # the actual runtime engine
```

- **javac** compiles `.java` source into `.class` files containing **bytecode** — a compact, typed instruction set for a stack-based virtual machine, not native CPU instructions
- Bytecode is platform-independent: the same `.class` file runs on any OS/architecture that has a JVM → this is what makes WORA work
- The JVM itself *is* architecture-specific (separate binary per platform); your code is not
- **Java 11+**: the standalone JRE download was discontinued — developers install the JDK, and production images are built with `jlink` to bundle only the modules the app needs

---

## JVM Architecture

- JVM is a C++ binary — runs like any other OS process
- A separate JVM binary exists per target architecture
- When Java needs hardware access, JNI calls C functions to make OS syscalls

```text
.java → javac → .class (bytecode)

JVM = entire runtime
  ├── ClassLoader          # loads .class files from disk/network into memory
  ├── Bytecode Verifier    # validates bytecode safety before execution
  ├── Memory Manager       # manages heap allocation and garbage collection
  │     ├── Heap           # object storage, managed by GC
  │     └── GC             # reclaims unreachable objects automatically
  ├── Thread Manager       # maps Java threads to OS threads, handles sync
  ├── JNI Bridge           # lets Java call native C/C++ code (and vice versa)
  └── Execution Engine     # runs the bytecode
        ├── Interpreter    # executes bytecode line-by-line (slow, no warmup needed)
        └── JIT Compiler   # compiles hot paths to native machine code (fast after warmup)
```

**Full execution path**:
`.class` → ClassLoader → Bytecode Verifier → Execution Engine (Interpreter / JIT) → Machine code → JNI → OS syscalls → Kernel → HW
- Kernel → HW: memory, CPU, DMA, bus → MMIO, interrupts, DMA

---

## Class Loading

Three phases executed sequentially for each class:

1. **Loading** — finds and reads the `.class` file into memory
2. **Linking**
   - **Verify**: Bytecode Verifier checks structural correctness and safety
   - **Prepare**: allocates static fields with zero/default values
   - **Resolve**: replaces symbolic references with direct memory references
3. **Initialization** — runs static initializers and `static {}` blocks in declaration order

### ClassLoader Hierarchy

Every class is loaded by exactly one ClassLoader. The JVM uses a **parent-first delegation** model:

```text
Bootstrap ClassLoader      # built into the JVM (C++); loads java.lang.*, java.util.*, etc.
  └── Platform ClassLoader # loads java.* platform modules (formerly Extension CL in Java 8)
        └── Application ClassLoader   # loads your classpath / module-path classes
              └── (custom ClassLoaders — frameworks, plugin systems, hot-reload tools)
```

- Before loading a class, each ClassLoader asks its parent first; only loads itself if the parent can't find it
- The same `.class` file loaded by two different ClassLoaders = **two distinct, incompatible types** in the JVM
- A class is only unloaded when its ClassLoader becomes unreachable and is GC'd — holding a reference to any class or instance keeps the entire ClassLoader alive
- This is why dynamic class generation (Javassist, cglib, Groovy scripts) can leak Metaspace: if the ClassLoader that defined those classes is never GC'd, neither are they

---

## Execution Engine

- **Interpreter**: decode → dispatch → execute → repeat (per instruction, no optimization, starts immediately)
- **JIT**: profiles running code → identifies hot methods (called ~10k+ times) → compiles to native machine code → stores in code cache → runs native from then on

**Tiered compilation** (default since Java 8): Interpreted → C1 → C2

| Tier | Compiler | Speed | Optimizations |
|------|----------|-------|---------------|
| 0 | Interpreter | Slowest | None — executes immediately |
| 1–3 | C1 (Client) | Fast compile | Inlining, dead code removal, basic opts |
| 4 | C2 (Server) | Slow compile | Escape analysis, loop unrolling, vectorization |

- C1 stops the interpreter quickly with lightweight optimizations
- C2 kicks in only for the hottest code paths with aggressive, expensive optimizations

---

## JVM Memory Areas

| Area | Per | GC-managed | Purpose |
|------|-----|------------|---------|
| Heap | JVM | Yes | All objects live here |
| Thread Stack | Thread | No | Stack frames for method calls |
| PC Register | Thread | No | Pointer to current bytecode instruction |
| Native Method Stack | Thread | No | Stack for JNI/native calls |
| Code Cache | JVM | No | JIT-compiled native machine code |
| Metaspace | JVM | No | Class metadata (replaced PermGen in Java 8+) |

- **Metaspace**: lives in native memory outside the heap, grows dynamically with no fixed size by default
- **Total JVM process memory** = Heap + Metaspace + Thread stacks + Code cache + Direct buffers (NIO)

### Heap Sizing Flags

- `-Xms`: initial heap size (reserved via `mmap()` at JVM startup)
- `-Xmx`: max heap ceiling — exceed it → `OutOfMemoryError`
- Everything outside the heap (Metaspace, threads, code cache) is governed by the cgroup memory limit
- In containers: always set `-Xmx` below the cgroup limit to avoid OOM kills from the kernel

---

## Memory Layout

**Java object header**: `[ Mark Word (8B) | Klass ptr (4–8B) | Fields | Padding ]`

| Field | Contents |
|-------|----------|
| Mark Word | hashCode, GC age (4 bits), lock state (unlocked / thin / fat) |
| Klass ptr | Compressed reference to class metadata in Metaspace |
| Padding | Ensures 8-byte alignment |

Lock state progression: `unlocked` → `thin` (CAS spin, no OS involvement) → `fat` (OS mutex, blocks on contention). Biased locking existed through Java 14 to optimize single-threaded access, but was removed in Java 15 (JEP 374) as its complexity outweighed the gains on modern hardware.

**Stack frame** (created per method call): `[ Local Var Array | Operand Stack | Frame Data ]`

| Field | Contents |
|-------|----------|
| Local Var Array | Slot 0 = `this`, then params, then local vars |
| Operand Stack | Scratch space — bytecode pushes/pops values here for calculations |
| Frame Data | Return address + reference to runtime constant pool |

---

## Garbage Collection

**Core rule**: garbage = any object not reachable from a GC root
- GC roots: thread stack locals, static fields, JNI references

### Generational Model

Generational hypothesis: most objects die young → optimize for that

```text
Heap
  ├── Young Gen (~30%)
  │     ├── Eden       # new objects allocated here
  │     ├── Survivor S0
  │     └── Survivor S1
  └── Old Gen / Tenured (~70%)   # long-lived objects promoted from Young
```

### TLAB — Thread-Local Allocation Buffers

Allocating from Eden naively would require a lock every time any thread creates any object. TLABs eliminate this:

- At startup (and after each GC), the JVM carves Eden into per-thread private chunks called TLABs
- Allocation inside a TLAB = a single pointer increment — no lock, no CAS, no inter-thread coordination
- When a TLAB is exhausted, the thread requests a new chunk from Eden (one brief sync point per thousands of allocations)
- Objects too large to fit in a TLAB are allocated directly in Eden or Old Gen (humongous objects in G1)

```text
Eden
  ├── TLAB for Thread-1  [ptr →                    ]
  ├── TLAB for Thread-2  [ptr →          ]
  ├── TLAB for Thread-3  [ptr →                         ]
  └── ... unassigned Eden space
```

This is why Java object allocation is nearly as fast as `malloc()` in C for the common case, despite running in a GC-managed heap. The cost of a GC is amortized over thousands of allocations, not paid per-object.

### Minor GC (Young Gen)

- Triggered when **Eden fills up**
- Stop-the-world, fast, frequent
- Eden + the active Survivor are scanned; live objects are **copied** to the other Survivor (copying avoids fragmentation)
- Objects surviving N cycles (default age threshold = 15) are **promoted to Old Gen**

### Major GC (Old Gen) — G1 Concurrent Cycle

Triggered when **Old Gen reaches ~45% occupancy** (configurable via `-XX:G1HeapOccupancyPercent`)

| Phase | Stop-the-world? | What happens |
|-------|-----------------|--------------|
| Initial Mark | Yes (piggybacked on Minor GC) | Marks GC roots |
| Concurrent Mark | No | Traverses full object reference graph while app runs |
| Remark | Yes | Finalizes marking; handles mutations during concurrent phase |
| Cleanup | Yes (brief) | Frees empty regions, sorts by liveness for mixed GC |
| Mixed GC | Yes (incremental) | Collects Young gen + selected Old gen regions |

**SATB (Snapshot At The Beginning)**: a snapshot of the heap reference graph is taken at the start of concurrent marking so concurrent mutations by the application don't corrupt the marking wave.

**Fallback — Serial Full GC**: if concurrent marking can't keep up (promotion rate > reclaim rate), G1 falls back to single-threaded Mark-and-Compact across the entire heap — all application threads stop for the full duration.

### GC Collectors

| Collector | Default since | Pause model | Best for |
|-----------|--------------|-------------|---------|
| Serial | Java 1 | Stop-the-world | Single-core / tiny heaps |
| Parallel | Java 1.4 | STW, parallel threads | Maximum throughput |
| G1 | Java 9 | Predictable pause targets | General-purpose |
| ZGC | Java 15 | Sub-millisecond concurrent | Large heaps, strict latency |
| Shenandoah | Java 12 | Sub-millisecond concurrent | Low-latency workloads |

### G1 Tuning Flags

```bash
# Start the concurrent cycle earlier (default 45%) — more headroom before Old Gen fills
-XX:G1HeapOccupancyPercent=35

# More concurrent GC threads — finishes the marking phase faster
-XX:ConcGCThreads=4

# More mixed GC rounds per cycle — drains Old Gen more aggressively
-XX:G1MixedGCCountTarget=12   # default 8

# Larger heap regions — raises the humongous object threshold
# Objects > half a region skip Young gen and go directly to Old Gen
-XX:G1HeapRegionSize=32m
```

### Reading GC Logs

Enable with `-Xlog:gc::time` (basic) or `-Xlog:gc*::time` (verbose).

**Basic log line** (`-Xlog:gc::time`):
```
[0.456s][info][gc] GC(3) Pause Young (G1 Evacuation Pause) 12M->4M(64M) 2.345ms
```

| Token | Meaning |
|-------|---------|
| `0.456s` | JVM uptime when GC started |
| `GC(3)` | 4th GC event (0-indexed) |
| `Pause Young` | Minor GC — only Young Gen collected |
| `G1 Evacuation Pause` | G1's name for copying live objects out of Young Gen |
| `12M->4M(64M)` | heap before → heap after (max heap); 8 MB reclaimed |
| `2.345ms` | all application threads were paused for this duration |

**Other pause types you'll see:**
- `Pause Young (Concurrent Start)` — Minor GC that also kicks off a concurrent marking cycle
- `Pause Remark` — STW phase to finalize concurrent marking
- `Pause Cleanup` — brief STW to sort regions by liveness
- `Pause Full (Ergonomics)` — fallback serial Full GC; if you see this regularly, tune G1 or increase heap

**Verbose line** (`-Xlog:gc*::time`) adds promoted bytes, Survivor occupancy, and per-region stats — useful for diagnosing promotion failures and tuning `-XX:MaxTenuringThreshold`.

---

## Threading

- **1:1 model**: each Java thread maps to exactly one OS thread (expensive, limited by OS scheduler)
- Thread creation and context switching have non-trivial overhead at high concurrency

### Virtual Threads (Java 21+, Project Loom)

- Lightweight threads managed entirely by the JVM, not the OS
- Many virtual threads multiplex onto a small pool of OS "carrier" threads
- When a virtual thread blocks on I/O, its carrier thread is released to run another virtual thread
- Enables millions of concurrent tasks without OS thread overhead
- Best suited for I/O-bound, high-concurrency workloads (e.g., HTTP servers, DB calls)

---

## References

- [openjdk/jdk](https://github.com/openjdk/jdk)
- [Java 21 GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
