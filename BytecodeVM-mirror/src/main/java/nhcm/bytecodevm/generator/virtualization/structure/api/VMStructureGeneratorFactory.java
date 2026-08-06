package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.virtualization.structure.callthreaded.CallThreadedVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.continuation.ContinuationPassingVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.coroutine.CoroutineVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.dataflow.DataFlowVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.distributeddispatch.DistributedDispatchVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.event.EventVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.fsm.FsmVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.graph.GraphVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.multipledispatch.MultipleDispatchVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.objectvm.ObjectVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.polymorphic.PolymorphicVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.recursive.RecursiveVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.registervm.RegisterVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.selfmodifying.SelfModifyingVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.simpledispatch.SimpleDispatchVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.threadeddirect.DirectThreadedVMGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.threadedindirect.IndirectThreadedVMGenerator;

public final class VMStructureGeneratorFactory
{
    private VMStructureGeneratorFactory()
    {
    }

    public static VMStructureGenerator create(VMStructure structure)
    {
        return switch (structure)
        {
            case SIMPLE_DISPATCH -> new SimpleDispatchVMGenerator();
            case DISTRIBUTED_DISPATCH -> new DistributedDispatchVMGenerator();
            case MULTIPLE_DISPATCH -> new MultipleDispatchVMGenerator();
            case THREADED_DIRECT -> new DirectThreadedVMGenerator();
            case THREADED_INDIRECT -> new IndirectThreadedVMGenerator();
            case CALL_THREADED -> new CallThreadedVMGenerator();
            case RECURSIVE -> new RecursiveVMGenerator();
            case CONTINUATION_PASSING -> new ContinuationPassingVMGenerator();
            case OBJECT -> new ObjectVMGenerator();
            case POLYMORPHIC -> new PolymorphicVMGenerator();
            case SELF_MODIFYING -> new SelfModifyingVMGenerator();
            case REGISTER_BASED -> new RegisterVMGenerator();
            case DATA_FLOW -> new DataFlowVMGenerator();
            case GRAPH -> new GraphVMGenerator();
            case FSM -> new FsmVMGenerator();
            case EVENT -> new EventVMGenerator();
            case COROUTINE -> new CoroutineVMGenerator();
            case LOW, MEDIUM, HIGH -> throw new IllegalArgumentException(
                    "Automatic VM structure must be resolved before generator selection: " + structure);
        };
    }
}
