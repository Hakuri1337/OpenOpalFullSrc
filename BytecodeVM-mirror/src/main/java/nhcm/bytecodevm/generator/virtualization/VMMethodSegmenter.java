package nhcm.bytecodevm.generator.virtualization;

import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.data.vminsn.VMOperand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds independently serializable VM method slices with local metadata. */
public final class VMMethodSegmenter
{
    private static final int HANDLER_SIZE = 4;

    private VMMethodSegmenter()
    {
    }

    public static VMMethod segment(
            VMMethod source,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        if (from < 0 || to > instructions.size() || from >= to)
        {
            throw new IllegalArgumentException(
                    "Invalid VM segment range [" + from + ", " + to + ")");
        }

        int startPc = instructions.get(from).programCounter;
        int endPc = instructions.get(to - 1).nextProgramCounter;
        int[] code = new int[endPc - startPc];
        List<Object> constants = new ArrayList<>();
        Map<Integer, Integer> constantIndexes = new HashMap<>();

        for (int instructionIndex = from; instructionIndex < to; instructionIndex++)
        {
            VMInstruction instruction = instructions.get(instructionIndex);
            int offset = instruction.programCounter - startPc;
            code[offset] = instruction.mutatedOpcode;
            for (VMOperand operand : instruction.operands)
            {
                code[offset + 1 + operand.index] = operand.constantReference
                        ? localConstant(source, operand.rawValue, constants, constantIndexes)
                        : operand.rawValue;
            }
        }

        int[] handlers = localExceptionHandlers(
                source,
                startPc,
                endPc,
                constants,
                constantIndexes);
        return new VMMethod(
                code,
                constants.toArray(),
                handlers,
                source.maxLocals,
                source.maxStack,
                source.getOpcMutator(),
                startPc,
                source.methodEndPc,
                source.getControlFlowLeaders());
    }

    private static int[] localExceptionHandlers(
            VMMethod source,
            int segmentStartPc,
            int segmentEndPc,
            List<Object> constants,
            Map<Integer, Integer> constantIndexes)
    {
        List<Integer> handlers = new ArrayList<>();
        for (int index = 0; index < source.exceptionHandlers.length; index += HANDLER_SIZE)
        {
            int startPc = source.exceptionHandlers[index];
            int endPc = source.exceptionHandlers[index + 1];
            if (segmentStartPc >= endPc || segmentEndPc <= startPc)
            {
                continue;
            }

            int typeIndex = source.exceptionHandlers[index + 3];
            handlers.add(startPc);
            handlers.add(endPc);
            handlers.add(source.exceptionHandlers[index + 2]);
            handlers.add(typeIndex < 0
                    ? -1
                    : localConstant(source, typeIndex, constants, constantIndexes));
        }

        int[] result = new int[handlers.size()];
        for (int index = 0; index < handlers.size(); index++)
        {
            result[index] = handlers.get(index);
        }
        return result;
    }

    private static int localConstant(
            VMMethod source,
            int originalIndex,
            List<Object> constants,
            Map<Integer, Integer> constantIndexes)
    {
        if (originalIndex < 0 || originalIndex >= source.constants.length)
        {
            throw new IllegalArgumentException("Invalid VM constant index: " + originalIndex);
        }
        Integer existing = constantIndexes.get(originalIndex);
        if (existing != null)
        {
            return existing;
        }
        int localIndex = constants.size();
        constants.add(source.constants[originalIndex]);
        constantIndexes.put(originalIndex, localIndex);
        return localIndex;
    }
}
