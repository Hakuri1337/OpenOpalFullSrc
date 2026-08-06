package nhcm.bytecodevm.sdk.annotation.config;

import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** VM overrides embedded in {@code @Virtualize}. */
@Retention(RetentionPolicy.CLASS)
@Target({})
public @interface VMOptions
{
    /** Selects a concrete VM structure or an automatic strength tier. */
    VMStructure structure() default VMStructure.CONFIG;

    /** Enables encrypted addresses, operands, opcode maps, dynamic constants, and state keys. */
    Toggle encrypt() default Toggle.CONFIG;

    /** Enables constant, stream, instruction-block, and virtual-CFG shuffling. */
    Toggle shuffle() default Toggle.CONFIG;

    /** Enables dispatch obfuscation and dynamic CodePool construction. */
    Toggle obfuscate() default Toggle.CONFIG;
}
