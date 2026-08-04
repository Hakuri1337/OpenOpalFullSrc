package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;

import java.util.Set;

public abstract class UnaryMathBranch extends InterpretBranch
{
    private final VMOpcode vmOpcode;

    protected UnaryMathBranch(VMOpcode vmOpcode)
    {
        this.vmOpcode = vmOpcode;
    }

    @Override
    public final Set<Opcs> opcodes()
    {
        return vmOpcode.getOpcodes();
    }

    @Override
    public final void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        if (!vmOpcode.contains(opcode))
        {
            throw new IllegalArgumentException(opcode + " is not handled by " + vmOpcode);
        }

        NumericType type = NumericType.fromOpcode(opcode);
        Local value = context.rightValue(type);
        popNumber(ib, context, type, value);
        pushNumber(ib, context, type, operation(value));
    }

    protected abstract Expr operation(Expr value);
}
