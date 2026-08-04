package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class StoreLocalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.STORE_LOCAL.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local localIndex = context.intLocal("localIndex", InterpretContext.RIGHT_VALUE);
        Local value = context.objectLocal("localValue", InterpretContext.LEFT_VALUE);
        context.nextOperand(ib, localIndex);
        popObject(ib, context, value);
        ib.setArray(context.locals(), localIndex, value);
    }
}
