package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.control;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class GotoBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.GOTO.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var jumpTarget = context.intLocal("jumpTarget", InterpretContext.JUMP_TARGET);
        context.nextOperand(ib, jumpTarget);
        ib.set(context.frameProgramCounter(), jumpTarget);
    }
}
