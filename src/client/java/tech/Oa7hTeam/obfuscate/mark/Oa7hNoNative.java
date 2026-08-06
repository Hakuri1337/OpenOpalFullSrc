package tech.Oa7hTeam.obfuscate.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a class or method out of native lowering, overriding a class-level {@link Oa7hNative}. Also
 * used to fence tool-generated helper methods from a downstream native compiler.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Oa7hNoNative {
}
