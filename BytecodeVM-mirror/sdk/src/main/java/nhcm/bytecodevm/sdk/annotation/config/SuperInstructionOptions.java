package nhcm.bytecodevm.sdk.annotation.config;

import nhcm.bytecodevm.sdk.enums.SuperInstructionMode;
import nhcm.bytecodevm.sdk.enums.Toggle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** SuperInstruction overrides embedded in {@code @Virtualize}. */
@Retention(RetentionPolicy.CLASS)
@Target({})
public @interface SuperInstructionOptions
{
    /** Sentinel used by numeric properties to inherit the YAML value. */
    int CONFIG = -1;

    Toggle enabled() default Toggle.CONFIG;

    SuperInstructionMode mode() default SuperInstructionMode.CONFIG;

    int combineMin() default CONFIG;

    int combineMax() default CONFIG;

    int minFrequency() default CONFIG;
}
