package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.control;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class ReturnBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.RETURN.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        if (!opcodes().contains(opcode))
        {
            throw new IllegalArgumentException("Unsupported return opcode: " + opcode);
        }

        if (opcode == Opcs.RETURN)
        {
            ib.set(context.frameReturnValue(), AdvInsnBuilder.nullValue("java/lang/Object"));
        }
        else
        {
            popObject(ib, context);
            ib.set(context.frameReturnValue(), context.stackObject());
        }
        ib.set(context.frameReturned(), AdvInsnBuilder.constant(true));
        ib.returnVoid();
    }

    @Override
    public boolean term(Opcs opcode)
    {
        return true;
    }
}
