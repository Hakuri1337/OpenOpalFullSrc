package tech.Oa7hTeam.obfuscate.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a class or method into invokedynamic reference obfuscation. Apply to code whose
 * method/field references should be routed through generated {@code invokedynamic} call sites.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Oa7hIndy {
}
