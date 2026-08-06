package nhcm.bytecodevm.data.vminsn;

import nhcm.bytecodevm.enums.Opcs;

import java.util.ArrayList;
import java.util.List;

public class SuperVMInstruction extends VMInstruction
{
    public final int superId;
    public final List<Opcs> sequence;
    public final int fusedInstructionCount;

    public SuperVMInstruction(
            int programCounter,
            int nextProgramCounter,
            int mutatedOpcode,
            int superId,
            List<VMInstruction> fusedInstructions)
    {
        super(
                programCounter,
                nextProgramCounter,
                mutatedOpcode,
                Opcs.SUPER_INSTRUCTION,
                flattenOperands(superId, fusedInstructions));
        this.superId = superId;
        this.sequence = fusedInstructions.stream().map(instruction -> instruction.opcode).toList();
        this.fusedInstructionCount = fusedInstructions.size();
    }

    private static List<VMOperand> flattenOperands(int superId, List<VMInstruction> fusedInstructions)
    {
        List<VMOperand> operands = new ArrayList<>();
        operands.add(new VMOperand(0, superId, false, superId));
        for (VMInstruction instruction : fusedInstructions)
        {
            for (VMOperand operand : instruction.operands)
            {
                operands.add(new VMOperand(
                        operands.size(),
                        operand.rawValue,
                        operand.constantReference,
                        operand.value));
            }
        }
        return operands;
    }
}
