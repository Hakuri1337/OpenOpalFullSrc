package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class SwapBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.SWAP.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context, InterpretContext.RIGHT_VALUE);
        popObject(ib, context, InterpretContext.LEFT_VALUE);
        pushObject(ib, context, InterpretContext.RIGHT_VALUE);
        pushObject(ib, context, InterpretContext.LEFT_VALUE);
    }
}
