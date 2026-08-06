package tech.Oa7hTeam.obfuscate.nativeobf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Excludes a class or method from a class-level {@link Oa7hInclude} selection. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Oa7hExclude {
}
