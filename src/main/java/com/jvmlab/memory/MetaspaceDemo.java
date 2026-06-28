package com.jvmlab.memory;

// MetaspaceDemo — demonstrates Metaspace exhaustion via dynamic class generation.
//
// Metaspace is a native memory region (NOT on the Java heap) that stores
// class metadata: bytecode, method descriptors, constant pool, field info.
// Every class loaded by the JVM consumes Metaspace. By default, Metaspace
// is unbounded and grows until the OS limit. With -XX:MaxMetaspaceSize
// you cap it — exceeding it throws OutOfMemoryError: Metaspace.
//
// In production, Metaspace leaks come from:
//   - Frameworks generating dynamic proxies (Spring, Hibernate, cglib)
//   - JSP engines recompiling pages
//   - Groovy/JRuby scripts creating new classes per execution
//   - OSGi containers loading plugins that are never unloaded
//
// Classes are only unloaded when their ClassLoader is GC'd. If you hold
// a reference to the ClassLoader (or any class it loaded), none of its
// classes can be unloaded — Metaspace grows forever.
//
// Run with: -XX:MaxMetaspaceSize=32m -Xlog:class+load=info
// Note: toClass() uses the context ClassLoader. For modular projects (module-info.java)
//       prefer ctClass.toClass(MethodHandles.lookup()) for proper module access.
// Watch:    class load log filling up, then OOM with "Metaspace" message.

import javassist.ClassPool;
import javassist.CtClass;

public class MetaspaceDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("Metaspace Demo — generating classes dynamically until OOM.");
        System.out.println("MaxMetaspaceSize: check your run config (-XX:MaxMetaspaceSize=32m)\n");

        ClassPool pool = ClassPool.getDefault();
        int count = 0;
        long startMs = System.currentTimeMillis();

        try {
            while (true) {
                // Generate a unique class name each iteration.
                // Each class is loaded into Metaspace and cannot be unloaded
                // because ClassPool holds a strong reference to the ClassLoader.
                String className = "com.jvmlab.generated.DynClass" + count;
                CtClass ctClass = pool.makeClass(className);

                // Add a field and a method to make it more realistic
                ctClass.addField(javassist.CtField.make("private int value = " + count + ";", ctClass));
                ctClass.addMethod(javassist.CtMethod.make(
                        "public int getValue() { return value; }", ctClass));

                ctClass.toClass(); // triggers actual class loading into Metaspace
                ctClass.detach();  // remove from pool but class stays loaded in JVM

                count++;

                if (count % 500 == 0) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    System.out.printf("Classes loaded: %,d | Time: %,d ms | Rate: %,d classes/sec%n",
                            count, elapsed, count * 1000L / Math.max(elapsed, 1));
                }
            }
        } catch (OutOfMemoryError e) {
            long elapsed = System.currentTimeMillis() - startMs;
            System.err.println("\n--- OutOfMemoryError: " + e.getMessage() + " ---");
            System.err.println("Classes loaded before OOM: " + count);
            System.err.println("Time elapsed: " + elapsed + " ms");
            System.err.println("Note: error says 'Metaspace' not 'Java heap space'");
            System.err.println("These are different memory regions — heap was fine.");
        }
    }
}