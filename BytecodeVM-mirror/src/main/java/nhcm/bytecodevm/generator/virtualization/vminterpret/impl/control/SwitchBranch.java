package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.control;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class SwitchBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.SWITCH.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popInt(ib, context, InterpretContext.SWITCH_KEY);

        if (opcode == Opcs.TABLESWITCH)
        {
            generateTableSwitch(ib, context);
        }
        else
        {
            generateLookupSwitch(ib, context);
        }

        ib.set(context.frameProgramCounter(), context.intLocal("jumpTarget", InterpretContext.JUMP_TARGET));
    }

    private static void generateTableSwitch(AdvInsnBuilder ib, InterpretContext context)
    {
        var min = context.intLocal("switchMin", InterpretContext.SWITCH_MIN);
        var ignoredMax = context.intLocal("switchMax", InterpretContext.SWITCH_CANDIDATE);
        var jumpTarget = context.intLocal("jumpTarget", InterpretContext.JUMP_TARGET);
        var count = context.intLocal("switchCount", InterpretContext.SWITCH_COUNT);
        var index = context.intLocal("switchIndex", InterpretContext.SWITCH_INDEX);
        var candidate = context.intLocal("switchCandidate", InterpretContext.SWITCH_CANDIDATE);

        context.nextOperand(ib, min);
        context.nextOperand(ib, ignoredMax);
        context.nextOperand(ib, jumpTarget);
        context.nextOperand(ib, count);

        ib.set(index, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.lessThan(index, count),
                b -> {
                    context.nextOperand(b, candidate);
                    b.ifCondition(
                            AdvInsnBuilder.equal(
                                    context.intLocal("switchKey", InterpretContext.SWITCH_KEY),
                                    AdvInsnBuilder.plus(min, index)),
                            match -> match.set(jumpTarget, candidate));
                    b.increment(index, 1);
                });
    }

    private static void generateLookupSwitch(AdvInsnBuilder ib, InterpretContext context)
    {
        var jumpTarget = context.intLocal("jumpTarget", InterpretContext.JUMP_TARGET);
        var count = context.intLocal("switchCount", InterpretContext.SWITCH_COUNT);
        var index = context.intLocal("switchIndex", InterpretContext.SWITCH_INDEX);
        var key = context.intLocal("lookupKey", InterpretContext.SWITCH_MIN);
        var candidate = context.intLocal("switchCandidate", InterpretContext.SWITCH_CANDIDATE);

        context.nextOperand(ib, jumpTarget);
        context.nextOperand(ib, count);

        ib.set(index, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.lessThan(index, count),
                b -> {
                    context.nextOperand(b, key);
                    context.nextOperand(b, candidate);
                    b.ifCondition(
                            AdvInsnBuilder.equal(
                                    context.intLocal("switchKey", InterpretContext.SWITCH_KEY),
                                    key),
                            match -> match.set(jumpTarget, candidate));
                    b.increment(index, 1);
                });
    }
}
