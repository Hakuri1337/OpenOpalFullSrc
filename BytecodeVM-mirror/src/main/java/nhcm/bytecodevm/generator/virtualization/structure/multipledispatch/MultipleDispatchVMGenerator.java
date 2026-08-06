package nhcm.bytecodevm.generator.virtualization.structure.multipledispatch;

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
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public final class MultipleDispatchVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public MultipleDispatchVMGenerator()
    {
        super(VMStructure.MULTIPLE_DISPATCH);
    }

    @Override
    public int stepBatchSize()
    {
        return 8;
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local result = runtime.intLocal("multipleDispatchResult", InterpretContext.OPCODE);
        ib.set(result, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(result, AdvInsnBuilder.constant(0)),
                variant -> variant.set(result, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        int variantCount = dispatch.plan().laneCount();
        List<String> variants = new ArrayList<>(variantCount);
        for (int variant = 0; variant < variantCount; variant++)
        {
            String name = dispatch.generation().methodName(
                    "dispatcherVariant$" + variant,
                    dispatch.dispatchDescriptor());
            List<VMDispatchTarget> order = new ArrayList<>(dispatch.targets());
            RandomUtils.shuffle(order);
            variants.add(name);
            dispatch.generation().addMethod(createVariant(dispatch, name, order, variant + 1));
        }

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local variant = runtime.intLocal("activeDispatcherVariant", InterpretContext.DISPATCH_SELECTOR + 1);
        Local result = runtime.intLocal("variantDispatchResult", InterpretContext.DISPATCH_SELECTOR + 2);
        ib.set(variant, AdvInsnBuilder.callStatic(
                "java/lang/Math",
                "floorMod",
                "I",
                dispatch.generation().mix(
                        runtime.frameStateKey(),
                        runtime.instructionIndex(),
                        AdvInsnBuilder.callVirtual(
                                runtime.program(),
                                dispatch.generation().programLayout().owner,
                                dispatch.generation().programLayout().methodKey.name(),
                                "I"),
                        AdvInsnBuilder.constant(dispatch.profile().saltHandler)),
                AdvInsnBuilder.constant(variantCount)));
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] choices = new java.util.function.Consumer[variantCount];
        for (int index = 0; index < variantCount; index++)
        {
            String name = variants.get(index);
            choices[index] = choice -> choice.set(result, dispatch.callDispatcher(name, runtime));
        }
        ib.switchTable(variant, 0, invalid -> invalid.set(result, AdvInsnBuilder.constant(0)), choices);
        dispatch.finishExternal(ib, result);
    }

    private MethodNode createVariant(
            VMDispatchGenerationContext dispatch,
            String name,
            List<VMDispatchTarget> targets,
            int variant)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                name,
                dispatch.dispatchDescriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        InterpretContext runtime = dispatch.generation().runtimeContext(null);
        Local passedInstructionIndex = ib.getLocal("instructionIndex", "I", 5);
        Local selector = ib.getLocal("variantSelector", "I", 6);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(selector, dispatch.variantSelector(selector, variant));
        List<SwitchCase> cases = new ArrayList<>();
        for (VMDispatchTarget target : targets)
        {
            cases.add(AdvInsnBuilder.switchCase(
                    dispatch.variantKey(target.key(), variant),
                    handler -> {
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
