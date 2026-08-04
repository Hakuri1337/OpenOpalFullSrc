package nhcm.bytecodevm.sdk.enums;

/** Controls how target-JAR calls reachable from an annotated method are treated. */
public enum CallPolicy
{
    CONFIG,
    NONE,
    INCLUDE,
    EXCLUDE
}
