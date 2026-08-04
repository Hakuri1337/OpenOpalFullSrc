package nhcm.bytecodevm.generator.virtualization.structure;

import nhcm.bytecodevm.enums.VMStructure;

public record VMStructurePlan(
        VMStructure structure,
        DispatchKind dispatchKind,
        SchedulerKind schedulerKind,
        int laneCount)
{
    public enum DispatchKind
    {
        CENTRAL,
        DISTRIBUTED,
        MULTIPLE,
        DIRECT_TOKEN,
        INDIRECT_TOKEN,
        CALL,
        OBJECT,
        POLYMORPHIC
    }

    public enum SchedulerKind
    {
        LOOP,
        CALL_TRAMPOLINE,
        RECURSIVE,
        CONTINUATION,
        SELF_MODIFYING,
        REGISTER,
        DATA_FLOW,
        GRAPH,
        FSM,
        EVENT,
        COROUTINE
    }

    public static VMStructurePlan forStructure(VMStructure structure)
    {
        if (structure.isAutomatic())
        {
            throw new IllegalArgumentException(structure + " must be resolved before VM generation");
        }
        return switch (structure)
        {
            case SIMPLE_DISPATCH -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.LOOP, 1);
            case DISTRIBUTED_DISPATCH -> new VMStructurePlan(structure, DispatchKind.DISTRIBUTED, SchedulerKind.LOOP, 4);
            case MULTIPLE_DISPATCH -> new VMStructurePlan(structure, DispatchKind.MULTIPLE, SchedulerKind.LOOP, 3);
            case THREADED_DIRECT -> new VMStructurePlan(structure, DispatchKind.DIRECT_TOKEN, SchedulerKind.LOOP, 4);
            case THREADED_INDIRECT -> new VMStructurePlan(structure, DispatchKind.INDIRECT_TOKEN, SchedulerKind.LOOP, 4);
            case CALL_THREADED -> new VMStructurePlan(structure, DispatchKind.CALL, SchedulerKind.CALL_TRAMPOLINE, 4);
            case RECURSIVE -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.RECURSIVE, 32);
            case CONTINUATION_PASSING -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.CONTINUATION, 4);
            case OBJECT -> new VMStructurePlan(structure, DispatchKind.OBJECT, SchedulerKind.LOOP, 4);
            case POLYMORPHIC -> new VMStructurePlan(structure, DispatchKind.POLYMORPHIC, SchedulerKind.LOOP, 3);
            case SELF_MODIFYING -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.SELF_MODIFYING, 1);
            case REGISTER_BASED -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.REGISTER, 8);
            case DATA_FLOW -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.DATA_FLOW, 8);
            case GRAPH -> new VMStructurePlan(structure, DispatchKind.DISTRIBUTED, SchedulerKind.GRAPH, 4);
            case FSM -> new VMStructurePlan(structure, DispatchKind.INDIRECT_TOKEN, SchedulerKind.FSM, 4);
            case EVENT -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.EVENT, 8);
            case COROUTINE -> new VMStructurePlan(structure, DispatchKind.CENTRAL, SchedulerKind.COROUTINE, 16);
            case LOW, MEDIUM, HIGH -> throw new IllegalStateException("Unresolved VM structure strength");
        };
    }

    public boolean usesDirectTokens()
    {
        return dispatchKind == DispatchKind.DIRECT_TOKEN;
    }

}
