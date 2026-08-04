package nhcm.bytecodevm.sdk.annotation;

import nhcm.bytecodevm.sdk.enums.Toggle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Includes a class in SDK-aware method matching without selecting every method
 * for virtualization. Use {@link Virtualize} on the class to virtualize all
 * eligible methods, or on individual methods for selective protection.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ProtectClass
{
    /** Controls ConstantValue relocation for this class. */
    Toggle constantFix() default Toggle.CONFIG;
}
