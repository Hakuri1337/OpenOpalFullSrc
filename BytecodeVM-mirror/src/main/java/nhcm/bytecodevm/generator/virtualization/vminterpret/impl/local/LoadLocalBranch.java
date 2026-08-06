package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class LoadLocalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_LOCAL.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local localIndex = context.intLocal("localIndex", InterpretContext.RIGHT_VALUE);
        context.nextOperand(ib, localIndex);
        if (opcode == Opcs.LLOAD || opcode == Opcs.DLOAD)
        {
            pushObjectWithWidth(ib, context, AdvInsnBuilder.arrayAt(context.locals(), localIndex), AdvInsnBuilder.constant(2));
        }
        else
        {
            pushObject(ib, context, AdvInsnBuilder.arrayAt(context.locals(), localIndex));
        }
    }
}
