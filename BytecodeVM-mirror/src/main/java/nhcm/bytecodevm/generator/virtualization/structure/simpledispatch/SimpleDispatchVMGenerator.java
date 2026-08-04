package nhcm.bytecodevm.generator.virtualization.structure.simpledispatch;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.advInsn.SwitchCase;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.virtualization.structure.api.AbstractVMStructureGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchTarget;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerationContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.ArrayList;
import java.util.List;

public final class SimpleDispatchVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public SimpleDispatchVMGenerator()
    {
        super(VMStructure.SIMPLE_DISPATCH);
    }

    @Override
    public int stepBatchSize()
    {
        return 32;
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local action = runtime.intLocal("simpleDispatchAction", InterpretContext.OPCODE);
        ib.set(action, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0)),
                loop -> loop.set(action, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("centralOpcodeSelector", InterpretContext.DISPATCH_SELECTOR);
        dispatch.setSelector(ib, runtime, selector);
        List<SwitchCase> cases = new ArrayList<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            cases.add(AdvInsnBuilder.switchCase(target.key(), handler -> {
                dispatch.emitTarget(handler, runtime, target, runtime.instructionIndex());
                handler.gotoLabel(dispatch.completed());
            }));
        }
        ib.switchLookup(
                selector,
                missing -> missing.gotoLabel(dispatch.unknown()),
                cases.toArray(SwitchCase[]::new));
    }
}
