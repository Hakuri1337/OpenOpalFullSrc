package nhcm.bytecodevm.data.vminsn;

import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.tools.OpcMutator;

import java.util.*;

public class VMMethod implements Iterable<VMInstruction>
{
    public final int[] code;
    public final Object[] constants;
    public final int[] exceptionHandlers;
    public final int maxLocals;
    public final int maxStack;
    public final int pcBase;
    public final int methodEndPc;

    private final OpcMutator opcMutator;
    private List<VMInstruction> instructionCache;
    private Map<Integer, VMInstruction> instructionByPc;
    private Set<Integer> controlFlowLeaders;

    public VMMethod(int[] code, Object[] constants, int maxLocals, int maxStack)
    {
        this(code, constants, new int[0], maxLocals, maxStack, null);
    }

    public VMMethod(
            int[] code,
            Object[] constants,
            int maxLocals,
            int maxStack,
            OpcMutator opcMutator)
    {
        this(code, constants, new int[0], maxLocals, maxStack, opcMutator);
    }

    public VMMethod(
            int[] code,
            Object[] constants,
            int[] exceptionHandlers,
            int maxLocals,
            int maxStack,
            OpcMutator opcMutator)
    {
        this(code, constants, exceptionHandlers, maxLocals, maxStack, opcMutator, 0, code.length);
    }

    public VMMethod(
            int[] code,
            Object[] constants,
            int[] exceptionHandlers,
            int maxLocals,
            int maxStack,
            OpcMutator opcMutator,
            int pcBase,
            int methodEndPc)
    {
        this(code, constants, exceptionHandlers, maxLocals, maxStack, opcMutator, pcBase, methodEndPc, null);
    }

    public VMMethod(
            int[] code,
            Object[] constants,
            int[] exceptionHandlers,
            int maxLocals,
            int maxStack,
            OpcMutator opcMutator,
            int pcBase,
            int methodEndPc,
            Set<Integer> controlFlowLeaders)
    {
        this.code = Objects.requireNonNull(code, "code");
        this.constants = Objects.requireNonNull(constants, "constants");
        this.exceptionHandlers = Objects.requireNonNull(exceptionHandlers, "exceptionHandlers");
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
        this.opcMutator = opcMutator;
        this.pcBase = pcBase;
        this.methodEndPc = methodEndPc;
        this.controlFlowLeaders = controlFlowLeaders == null
                ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(controlFlowLeaders));
    }

    public synchronized List<VMInstruction> getInstructions()
    {
        if (opcMutator == null)
        {
            throw new IllegalStateException(
                    "This VMMethod has no OpcMutator; use getInstructions(mutator)");
        }
        if (instructionCache == null)
        {
            instructionCache = decodeInstructions(opcMutator);
            Map<Integer, VMInstruction> byPc = new HashMap<>();
            for (VMInstruction instruction : instructionCache)
            {
                byPc.put(instruction.programCounter, instruction);
            }
            instructionByPc = Collections.unmodifiableMap(byPc);
        }
        return instructionCache;
    }

    public List<VMInstruction> getInstructions(OpcMutator mutator)
    {
        Objects.requireNonNull(mutator, "mutator");
        if (mutator == opcMutator)
        {
            return getInstructions();
        }
        return decodeInstructions(mutator);
    }

    private List<VMInstruction> decodeInstructions(OpcMutator mutator)
    {
        List<VMInstruction> instructions = new ArrayList<>();
        int pc = 0;

        while (pc < code.length)
        {
            int instructionPc = pcBase + pc;
            int mutatedOpcode = code[pc++];
            Opcs opcode = mutator.fromMutated(mutatedOpcode);

            if (opcode == null)
            {
                throw malformed("Unknown opcode " + mutatedOpcode, instructionPc);
            }

            int operandCount;
            try
            {
                operandCount = opcode.getOperandCount(code, pc);
            }
            catch (RuntimeException exception)
            {
                throw malformed("Cannot read operands for " + opcode, instructionPc, exception);
            }

            if (operandCount < 0 || pc + operandCount > code.length)
            {
                throw malformed("Truncated operands for " + opcode, instructionPc);
            }

            List<VMOperand> operands = new ArrayList<>(operandCount);
            for (int operandIndex = 0; operandIndex < operandCount; operandIndex++)
            {
                int rawValue = code[pc++];
                boolean constantReference = opcode.isConstantOperand(operandIndex);
                Object value = rawValue;

                if (constantReference)
                {
                    if (rawValue < 0 || rawValue >= constants.length)
                    {
                        throw malformed(
                                "Invalid constant #" + rawValue + " for " + opcode,
                                instructionPc);
                    }
                    value = constants[rawValue];
                }

                operands.add(new VMOperand(
                        operandIndex,
                        rawValue,
                        constantReference,
                        value));
            }

            instructions.add(new VMInstruction(
                    instructionPc,
                    pcBase + pc,
                    mutatedOpcode,
                    opcode,
                    operands));
        }

        return Collections.unmodifiableList(instructions);
    }

    public VMInstruction instructionAt(int pc)
    {
        getInstructions();
        VMInstruction instruction = instructionByPc.get(pc);
        if (instruction != null) return instruction;
        throw new NoSuchElementException("No VM instruction at pc " + pc);
    }

    public OpcMutator getOpcMutator()
    {
        return opcMutator;
    }

    public synchronized Set<Integer> getControlFlowLeaders()
    {
        if (controlFlowLeaders != null)
        {
            return controlFlowLeaders;
        }
        LinkedHashSet<Integer> leaders = new LinkedHashSet<>();
        List<VMInstruction> instructions = getInstructions();
        if (!instructions.isEmpty())
        {
            leaders.add(instructions.getFirst().programCounter);
        }
        for (VMInstruction instruction : instructions)
        {
            for (int operandIndex = 0; operandIndex < instruction.operandCount(); operandIndex++)
            {
                if (isJumpTargetOperand(instruction.opcode, operandIndex))
                {
                    leaders.add(instruction.operand(operandIndex).rawValue);
                }
            }
        }
        for (int index = 0; index < exceptionHandlers.length; index += 4)
        {
            leaders.add(exceptionHandlers[index]);
            leaders.add(exceptionHandlers[index + 1]);
            leaders.add(exceptionHandlers[index + 2]);
        }
        controlFlowLeaders = Collections.unmodifiableSet(leaders);
        return controlFlowLeaders;
    }

    private static boolean isJumpTargetOperand(Opcs opcode, int operandIndex)
    {
        return switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL, GOTO -> operandIndex == 0;
            case TABLESWITCH -> operandIndex == 2 || operandIndex >= 4;
            case LOOKUPSWITCH -> operandIndex == 0 || (operandIndex >= 3 && (operandIndex & 1) == 1);
            default -> false;
        };
    }

    @Override
    public Iterator<VMInstruction> iterator()
    {
        return getInstructions().iterator();
    }

    private static IllegalArgumentException malformed(String message, int pc)
    {
        return new IllegalArgumentException(message + " at pc " + pc);
    }

    private static IllegalArgumentException malformed(
            String message,
            int pc,
            RuntimeException cause)
    {
        return new IllegalArgumentException(message + " at pc " + pc, cause);
    }
}
