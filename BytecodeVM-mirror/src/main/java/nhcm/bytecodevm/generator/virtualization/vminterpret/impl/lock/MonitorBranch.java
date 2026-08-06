package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lock;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class MonitorBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.MONITOR.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context);
        switch (opcode)
        {
            case MONITORENTER -> ib.directCall(AdvInsnBuilder.callStatic(
                    context.vm.owner,
                    context.vm.monitorEnter.name(),
                    "V",
                    context.stackObject()));
            case MONITOREXIT -> ib.directCall(AdvInsnBuilder.callStatic(
                    context.vm.owner,
                    context.vm.monitorExit.name(),
                    "V",
                    context.stackObject()));
            default -> throw new IllegalArgumentException("Unsupported monitor opcode: " + opcode);
        }
    }
}
