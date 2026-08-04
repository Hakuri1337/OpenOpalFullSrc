package nhcm.bytecodevm.generator.virtualization.structure.distributeddispatch;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.advInsn.SwitchCase;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.virtualization.structure.api.AbstractVMStructureGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchTarget;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerationContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public final class DistributedDispatchVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public DistributedDispatchVMGenerator()
    {
        super(VMStructure.DISTRIBUTED_DISPATCH);
    }

    @Override
    public int stepBatchSize()
    {
        return 16;
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local pulse = runtime.intLocal("distributedPulse", InterpretContext.OPCODE);
        ib.set(pulse, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(pulse, AdvInsnBuilder.constant(0)),
                shard -> shard.set(pulse, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        int laneCount = dispatch.plan().laneCount();
        List<List<VMDispatchTarget>> lanes = new ArrayList<>(laneCount);
        for (int lane = 0; lane < laneCount; lane++)
        {
            lanes.add(new ArrayList<>());
        }
        for (VMDispatchTarget target : dispatch.targets())
        {
            lanes.get(Math.floorMod(target.key(), laneCount)).add(target);
        }

        List<String> shards = new ArrayList<>(laneCount);
        for (int lane = 0; lane < laneCount; lane++)
        {
            String name = dispatch.generation().methodName(
                    "distributedDispatchShard$" + lane,
                    dispatch.dispatchDescriptor());
            shards.add(name);
            dispatch.generation().addMethod(createShard(dispatch, name, lanes.get(lane)));
        }

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("distributedSelector", InterpretContext.DISPATCH_SELECTOR);
        Local lane = runtime.intLocal("distributedLane", InterpretContext.DISPATCH_SELECTOR + 1);
        Local result = runtime.intLocal("distributedResult", InterpretContext.DISPATCH_SELECTOR + 2);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(lane, AdvInsnBuilder.callStatic(
                "java/lang/Math",
                "floorMod",
                "I",
                selector,
                AdvInsnBuilder.constant(laneCount)));
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] laneCases = new java.util.function.Consumer[laneCount];
        for (int index = 0; index < laneCount; index++)
        {
            String shard = shards.get(index);
            laneCases[index] = branch -> branch.set(result, dispatch.callDispatcher(shard, runtime));
        }
        ib.switchTable(lane, 0, invalid -> invalid.set(result, AdvInsnBuilder.constant(0)), laneCases);
        dispatch.finishExternal(ib, result);
    }

    private MethodNode createShard(
            VMDispatchGenerationContext dispatch,
            String name,
            List<VMDispatchTarget> targets)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                name,
                dispatch.dispatchDescriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        InterpretContext runtime = dispatch.generation().runtimeContext(null);
        Local passedInstructionIndex = ib.getLocal("instructionIndex", "I", 5);
        Local selector = ib.getLocal("shardSelector", "I", 6);
        dispatch.setSelector(ib, runtime, selector);
        List<SwitchCase> cases = new ArrayList<>();
        for (VMDispatchTarget target : targets)
        {
            cases.add(AdvInsnBuilder.switchCase(target.key(), handler -> {
                dispatch.emitTarget(handler, runtime, target, passedInstructionIndex);
                handler.returnValue(AdvInsnBuilder.constant(1));
            }));
        }
        ib.switchLookup(
                selector,
                missing -> missing.returnValue(AdvInsnBuilder.constant(0)),
                cases.toArray(SwitchCase[]::new));
        return method;
    }
}
