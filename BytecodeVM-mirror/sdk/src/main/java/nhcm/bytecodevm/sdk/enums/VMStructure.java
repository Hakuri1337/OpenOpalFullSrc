package nhcm.bytecodevm.sdk.enums;

/**
 * VM structure override exposed by the annotation SDK.
 * {@code LOW}, {@code MEDIUM}, and {@code HIGH} use the same ranked candidate
 * pools documented by the main BytecodeVM configuration.
 */
public enum VMStructure
{
    CONFIG,
    SIMPLE_DISPATCH,
    DISTRIBUTED_DISPATCH,
    MULTIPLE_DISPATCH,
    THREADED_DIRECT,
    THREADED_INDIRECT,
    CALL_THREADED,
    RECURSIVE,
    CONTINUATION_PASSING,
    OBJECT,
    POLYMORPHIC,
    SELF_MODIFYING,
    REGISTER_BASED,
    DATA_FLOW,
    GRAPH,
    FSM,
    EVENT,
    COROUTINE,
    LOW,
    MEDIUM,
    HIGH
}
