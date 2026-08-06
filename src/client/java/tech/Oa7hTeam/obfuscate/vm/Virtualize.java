package tech.Oa7hTeam.obfuscate.vm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method whose body should be virtualized: its bytecode is lowered to a
 * custom instruction set and executed by a generated interpreter instead of running directly
 * on the JVM. Static analysis then sees only the encoded program and the interpreter loop,
 * not the original control flow.
 *
 * <p>Applied to a class, every eligible method of that class is targeted. Applied to a method,
 * only that method is targeted. A method is only virtualized when the compiler can lower it
 * losslessly; unsupported shapes (constructors/static initializers, methods with
 * {@code try/catch}, {@code jsr}/{@code ret}, {@code invokedynamic}, or opcodes outside the
 * supported set) are left as ordinary bytecode, so output always runs.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}: the marker survives into the compiled jar for
 * the obfuscator to read, but is stripped from the output and never observed at runtime. Use
 * {@link VirtualizeExclude} to opt a specific method out of a class-level {@code @Virtualize}.
 *
 * @author github.com/AirFoundation
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Virtualize {
}
