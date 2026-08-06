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

public abstract class ShiftMathBranch extends InterpretBranch
{
    private final VMOpcode vmOpcode;

    protected ShiftMathBranch(VMOpcode vmOpcode)
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

        NumericType valueType = NumericType.fromOpcode(opcode);
        Local distance = context.rightValue(NumericType.INT);
        Local value = context.leftValue(valueType);
        popNumber(ib, context, NumericType.INT, distance);
        popNumber(ib, context, valueType, value);
        pushNumber(ib, context, valueType, operation(value, distance));
    }

    protected abstract Expr operation(Expr value, Expr distance);
}
