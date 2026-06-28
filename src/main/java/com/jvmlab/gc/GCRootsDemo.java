package com.jvmlab.gc;

// GCRootsDemo — demonstrates what makes an object reachable vs collectable.
//
// The GC does not scan the entire heap looking for dead objects.
// It starts from GC roots — a fixed set of always-reachable references:
//   1. Local variables on any thread's stack
//   2. Static fields of loaded classes
//   3. JNI references (native code holding Java objects)
//   4. Active thread objects themselves
//
// The GC traverses the object graph from these roots. Any object
// reachable by following references from a root is "live" and kept.
// Anything not reachable is dead and its memory is reclaimed.
// Nulling a reference does not free memory — it removes one path
// in the graph. The object is only collected when NO path from any
// root reaches it.
//
// Run with: -Xlog:gc::time
// Watch:    console output shows which references kept objects alive.

public class GCRootsDemo {

    // ROOT TYPE 1: static field — lives as long as the class is loaded.
    // The class is loaded as long as the JVM runs.
    // Objects referenced here are NEVER collected during normal operation.
    static Object staticRoot = new Object();

    // A simple node to build an object graph
    static class Node {
        final String name;
        Node next;

        Node(String name) { this.name = name; }

        @Override
        public String toString() { return "Node(" + name + ")"; }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== GC Roots Demo ===\n");

        // ROOT TYPE 2: local variable on the stack.
        // 'stackRoot' is reachable as long as this method is executing.
        Node stackRoot = new Node("stack-root");
        Node child     = new Node("child");
        Node grandchild = new Node("grandchild");

        // Build a reference chain: stackRoot → child → grandchild
        stackRoot.next = child;
        child.next     = grandchild;

        System.out.println("Before GC:");
        System.out.println("  stackRoot    reachable: " + (stackRoot != null));
        System.out.println("  child        reachable: " + (stackRoot.next != null));
        System.out.println("  grandchild   reachable: " + (stackRoot.next.next != null));
        System.out.println("  staticRoot   reachable: " + (staticRoot != null));

        // Cut the chain at 'child' — grandchild is now unreachable
        // because the only path to it was stackRoot → child → grandchild
        // and we just severed child → grandchild.
        // Note: child itself is still reachable via stackRoot → child.
        child.next = null;

        // Also null the static root — now unreachable from static fields
        staticRoot = null;

        System.out.println("\nAfter nulling child.next and staticRoot:");
        System.out.println("  stackRoot  still reachable (local var): " + (stackRoot != null));
        System.out.println("  child      still reachable (via stackRoot.next): " + (stackRoot.next != null));
        System.out.println("  grandchild now unreachable — eligible for GC");
        System.out.println("  staticRoot now unreachable — eligible for GC");

        // Request GC — not guaranteed to run immediately but usually does
        System.out.println("\nRequesting GC...");
        System.gc();
        Thread.sleep(200);

        System.out.println("\nAfter GC:");
        System.out.println("  stackRoot still alive: " + (stackRoot != null));
        System.out.println("  child     still alive: " + (stackRoot.next != null));
        System.out.println("  grandchild and staticRoot were collected (no path from any root)");

        // stackRoot goes out of scope when main() returns — then it too is eligible
        System.out.println("\nmain() returning — stackRoot and child become eligible for GC");
    }
}