package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lowered;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public final class RegisterOperationBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return Set.of(Opcs.REGISTER_OP);
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local semantic = context.intLocal("registerSemantic", InterpretContext.RIGHT_VALUE);
        Local destination = context.intLocal("registerDestination", InterpretContext.LEFT_VALUE);
        Local sourceA = context.intLocal("registerSourceA", InterpretContext.MIDDLE_VALUE);
        Local sourceB = context.intLocal("registerSourceB", InterpretContext.MIDDLE_VALUE + 1);
        Local auxiliary = context.intLocal("registerAuxiliary", InterpretContext.MIDDLE_VALUE + 2);
        Local delta = context.intLocal("registerDelta", InterpretContext.MIDDLE_VALUE + 3);
        Local width = context.intLocal("registerWidth", InterpretContext.MIDDLE_VALUE + 4);
        Local baseDelta = context.intLocal("registerBaseDelta", InterpretContext.MIDDLE_VALUE + 5);
        Local baseStack = context.intLocal("registerBaseStack", InterpretContext.MIDDLE_VALUE + 6);

        context.nextOperand(ib, semantic);
        context.nextOperand(ib, destination);
        context.nextOperand(ib, sourceA);
        context.nextOperand(ib, sourceB);
        context.nextOperand(ib, auxiliary);
        context.nextOperand(ib, delta);
        context.nextOperand(ib, width);
        context.nextOperand(ib, baseDelta);

        ib.set(baseStack, context.frameField(context.frame.stackPointer));
        ib.directCall(AdvInsnBuilder.callStatic(
                context.vmClassName,
                context.vm.executeRegisterOp.name(),
                "V",
                context.program(),
                context.frame(),
                context.constants(),
                semantic,
                AdvInsnBuilder.plus(baseStack, baseDelta),
                destination,
                sourceA,
                sourceB,
                auxiliary,
                width,
                context.instructionIndex(),
                context.opcode()));
        ib.set(
                context.frameField(context.frame.stackPointer),
                AdvInsnBuilder.plus(baseStack, delta));
    }
}
