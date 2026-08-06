package tech.Oa7hTeam.obfuscate.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as native-owned: opts it into native (bytecode-to-C) lowering by a
 * downstream native compiler and fences its body from the Java hardening passes so the original
 * body is preserved for lowering.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Oa7hNative {
}
