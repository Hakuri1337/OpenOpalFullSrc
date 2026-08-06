package nhcm.bytecodevm.generator.virtualization.structure.recursive;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecursiveVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public RecursiveVMGenerator()
    {
        super(VMStructure.RECURSIVE);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        String descriptor = generation.schedulerDescriptor(true);
        String recursiveName = generation.methodName("recursiveExecution", descriptor);
        generation.addMethod(createRecursiveMethod(generation, recursiveName, descriptor));

        Local action = runtime.intLocal("recursiveAction", InterpretContext.OPCODE);
        ib.set(action, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0)),
                trampoline -> trampoline.set(action, AdvInsnBuilder.callStatic(
                        generation.owner(),
                        recursiveName,
                        "I",
                        runtime.program(),
                        runtime.frame(),
                        runtime.code(),
                        runtime.constants(),
                        AdvInsnBuilder.constant(0))));
    }

    private MethodNode createRecursiveMethod(
            VMStructureGenerationContext generation,
            String name,
            String descriptor)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        InterpretContext runtime = generation.runtimeContext(null);
        Local depth = ib.getLocal("recursionDepth", "I", 4);
        Local action = ib.getLocal("recursiveAction", "I", 5);
        ib.ifCondition(
                AdvInsnBuilder.greaterOrEqual(depth, AdvInsnBuilder.constant(generation.plan().laneCount())),
                yield -> yield.returnValue(AdvInsnBuilder.constant(0)));
        ib.set(action, generation.step(runtime));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(action, AdvInsnBuilder.constant(0)),
                done -> done.returnValue(action));
        ib.returnValue(AdvInsnBuilder.callStatic(
                generation.owner(),
                name,
                "I",
                runtime.program(),
                runtime.frame(),
                runtime.code(),
                runtime.constants(),
                AdvInsnBuilder.plus(depth, AdvInsnBuilder.constant(1))));
        return method;
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("recursiveSelector", InterpretContext.DISPATCH_SELECTOR);
        dispatch.setSelector(dispatch.instructions(), runtime, selector);
        Map<Integer, VMDispatchTarget> unique = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            unique.putIfAbsent(target.key(), target);
        }
        List<VMDispatchTarget> targets = new ArrayList<>(unique.values());
        targets.sort(Comparator.comparingInt(VMDispatchTarget::key));
        emitDecisionTree(dispatch, dispatch.instructions(), runtime, selector, targets, 0, targets.size());
    }

    private void emitDecisionTree(
            VMDispatchGenerationContext dispatch,
            AdvInsnBuilder ib,
            InterpretContext runtime,
            Local selector,
            List<VMDispatchTarget> targets,
            int from,
            int to)
    {
        if (from >= to)
        {
            ib.gotoLabel(dispatch.unknown());
            return;
        }
        int middle = (from + to) >>> 1;
        VMDispatchTarget target = targets.get(middle);
        ib.ifElse(
                AdvInsnBuilder.equal(selector, AdvInsnBuilder.constant(target.key())),
                match -> {
                    dispatch.emitTarget(match, runtime, target, runtime.instructionIndex());
                    match.gotoLabel(dispatch.completed());
                },
                mismatch -> mismatch.ifElse(
                        AdvInsnBuilder.lessThan(selector, AdvInsnBuilder.constant(target.key())),
                        lower -> emitDecisionTree(dispatch, lower, runtime, selector, targets, from, middle),
                        higher -> emitDecisionTree(dispatch, higher, runtime, selector, targets, middle + 1, to)));
    }
}
