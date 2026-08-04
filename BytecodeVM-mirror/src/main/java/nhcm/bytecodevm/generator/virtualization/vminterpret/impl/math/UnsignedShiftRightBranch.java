package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts.ShiftMathBranch;

public class UnsignedShiftRightBranch extends ShiftMathBranch
{
    public UnsignedShiftRightBranch()
    {
        super(VMOpcode.UNSIGNED_SHIFT_RIGHT);
    }

    @Override
    protected Expr operation(Expr value, Expr distance)
    {
        return AdvInsnBuilder.unsignedShiftRight(value, distance);
    }
}
