package tech.Oa7hTeam.obfuscate.vm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a single method out of virtualization when its declaring class carries a class-level
 * {@link Virtualize}. Mirrors the {@code @Oa7hExclude} pattern used for native obfuscation.
 *
 * @author github.com/AirFoundation
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface VirtualizeExclude {
}
