package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts.ShiftMathBranch;

public class ShiftRightBranch extends ShiftMathBranch
{
    public ShiftRightBranch()
    {
        super(VMOpcode.SHIFT_RIGHT);
    }

    @Override
    protected Expr operation(Expr value, Expr distance)
    {
        return AdvInsnBuilder.shiftRight(value, distance);
    }
}
