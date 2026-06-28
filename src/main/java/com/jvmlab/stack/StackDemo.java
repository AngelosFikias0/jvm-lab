package com.jvmlab.stack;

// StackDemo — demonstrates the JVM thread stack and stack frame model.
//
// Every thread has its own stack, sized by -Xss (default ~512KB–1MB).
// Each method call pushes a new frame onto the stack containing:
//   - Local variable array (includes 'this' for instance methods)
//   - Operand stack (working area for bytecode instructions)
//   - Frame metadata (return address, reference to constant pool)
//
// When recursion goes too deep, the stack exhausts its allocated memory
// and the JVM throws StackOverflowError — not an exception from your
// code but from the JVM itself detecting the overflow condition.
//
// This demo runs three experiments:
//   1. Plain recursion — how deep can we go with default stack?
//   2. Recursion with a large local array — fewer frames fit
//   3. Tail-style recursion with an accumulator — same depth, different shape
//
// Run with: -Xss256k   (small stack  — low depth)
// Then try: -Xss2m     (larger stack — much higher depth)
// Observe how depth scales linearly with stack size.

public class StackDemo {

    // --- Experiment 1: plain recursion ---
    static int plainDepth = 0;

    static void plainRecurse() {
        plainDepth++;
        plainRecurse(); // each call adds one frame to the stack
    }

    // --- Experiment 2: recursion with local state ---
    // Each frame holds a 512-byte array, consuming more stack space per frame.
    // Fewer frames fit before StackOverflowError — depth will be much lower.
    static int heavyDepth = 0;

    static void heavyRecurse() {
        byte[] localData = new byte[512]; // 512 bytes of local state per frame
        localData[0] = (byte) heavyDepth; // touch it so JIT doesn't optimize away
        heavyDepth++;
        heavyRecurse();
    }

    // --- Experiment 3: accumulator style ---
    // Logically tail-recursive but Java does NOT optimize tail calls.
    // The JIT does not eliminate these frames — depth is the same as plain.
    static int accDepth = 0;

    static long accumulatorRecurse(long acc) {
        accDepth++;
        return accumulatorRecurse(acc + accDepth); // still pushes a frame
    }

    public static void main(String[] args) {
        System.out.println("Stack size (-Xss): check your run config");
        System.out.println("Default is ~512KB. Try -Xss256k vs -Xss2m.\n");

        // Experiment 1
        try {
            plainRecurse();
        } catch (StackOverflowError e) {
            System.out.println("Experiment 1 — plain recursion");
            System.out.println("  Max depth: " + plainDepth + "\n");
        }

        // Experiment 2
        try {
            heavyRecurse();
        } catch (StackOverflowError e) {
            System.out.println("Experiment 2 — recursion with 512B local array per frame");
            System.out.println("  Max depth: " + heavyDepth);
            System.out.println("  Much lower — each frame consumes more stack space\n");
        }

        // Experiment 3
        try {
            accumulatorRecurse(0);
        } catch (StackOverflowError e) {
            System.out.println("Experiment 3 — accumulator recursion (no tail-call opt in JVM)");
            System.out.println("  Max depth: " + accDepth);
            System.out.println("  Same as plain — Java never eliminates tail calls\n");
        }

        System.out.println("Now change -Xss and rerun. Depth scales linearly with stack size.");
    }
}
