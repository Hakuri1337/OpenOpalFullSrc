package nhcm.bytecodevm.generator.virtualization.superinstruction;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.vminsn.SuperVMInstruction;
import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.virtualization.ProtectedVMMethod;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.utils.RandomUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SuperInstructionCombiner
{
    private SuperInstructionCombiner()
    {
    }

    public static void prepare(
            List<CompiledMethod> methods,
            BytecodeVMConfig fallbackConfig,
            SuperInstructionRegistry registry)
    {
        Map<List<Opcs>, Integer> frequency = new HashMap<>();
        for (CompiledMethod method : methods)
        {
            BytecodeVMConfig config = method.config == null ? fallbackConfig : method.config;
            if (!usesPatterns(config))
            {
                continue;
            }
            for (List<VMInstruction> block : splitSafeBlocks(method.vmMethod, method.vmMethod.getInstructions()))
            {
                countPatterns(block, config, frequency);
            }
        }

        frequency.entrySet()
                .stream()
                .filter(entry -> entry.getValue() >= fallbackConfig.superInstructionMinFrequency)
                .sorted((left, right) -> {
                    int byFrequency = Integer.compare(right.getValue(), left.getValue());
                    if (byFrequency != 0)
                    {
                        return byFrequency;
                    }
                    int byLength = Integer.compare(right.getKey().size(), left.getKey().size());
                    if (byLength != 0)
                    {
                        return byLength;
                    }
                    return signature(left.getKey()).compareTo(signature(right.getKey()));
                })
                .forEach(entry -> registry.register(entry.getKey()));
    }

    public static List<VMInstruction> combine(
            VMMethod method,
            BytecodeVMConfig config,
            SuperInstructionRegistry registry,
            OpcMutator mutator)
    {
        return combine(method, method.getInstructions(), config, registry, mutator);
    }

    public static List<VMInstruction> combine(
            VMMethod method,
            List<VMInstruction> instructions,
            BytecodeVMConfig config,
            SuperInstructionRegistry registry,
            OpcMutator mutator)
    {
        if (!config.superInstruction || instructions.size() < config.superInstructionCombineMin)
        {
            return instructions;
        }

        List<VMInstruction> result = new ArrayList<>(instructions.size());
        for (List<VMInstruction> block : splitSafeBlocks(method, instructions))
        {
            combineBlock(block, config, registry, mutator, result);
        }
        return List.copyOf(result);
    }

    private static boolean usesPatterns(BytecodeVMConfig config)
    {
        return config.superInstruction &&
                (config.superInstructionMode == BytecodeVMConfig.SuperInstructionMode.PATTERN ||
                 config.superInstructionMode == BytecodeVMConfig.SuperInstructionMode.HYBRID);
    }

    private static void countPatterns(
            List<VMInstruction> block,
            BytecodeVMConfig config,
            Map<List<Opcs>, Integer> frequency)
    {
        for (int index = 0; index < block.size(); index++)
        {
            int maxLength = maxFusableLength(block, index, config.superInstructionCombineMax);
            for (int length = config.superInstructionCombineMin; length <= maxLength; length++)
            {
                List<Opcs> sequence = sequence(block, index, length);
                frequency.merge(sequence, 1, Integer::sum);
            }
        }
    }

    private static void combineBlock(
            List<VMInstruction> block,
            BytecodeVMConfig config,
            SuperInstructionRegistry registry,
            OpcMutator mutator,
            List<VMInstruction> output)
    {
        int index = 0;
        while (index < block.size())
        {
            FusedRange pattern = matchPattern(block, index, config, registry);
            if (pattern != null)
            {
                output.add(createSuperInstruction(block, index, pattern.length, pattern.recipe, mutator));
                index += pattern.length;
                continue;
            }

            if (config.superInstructionMode != BytecodeVMConfig.SuperInstructionMode.PATTERN)
            {
                FusedRange random = randomRange(block, index, config, registry);
                if (random != null)
                {
                    output.add(createSuperInstruction(block, index, random.length, random.recipe, mutator));
                    index += random.length;
                    continue;
                }
            }

            output.add(block.get(index++));
        }
    }

    private static FusedRange matchPattern(
            List<VMInstruction> block,
            int index,
            BytecodeVMConfig config,
            SuperInstructionRegistry registry)
    {
        if (config.superInstructionMode == BytecodeVMConfig.SuperInstructionMode.RANDOM)
        {
            return null;
        }
        int maxLength = maxFusableLength(block, index, config.superInstructionCombineMax);
        for (int length = maxLength; length >= config.superInstructionCombineMin; length--)
        {
            SuperInstructionRegistry.Recipe recipe = registry.find(sequence(block, index, length));
            if (recipe != null)
            {
                return new FusedRange(length, recipe);
            }
        }
        return null;
    }

    private static FusedRange randomRange(
            List<VMInstruction> block,
            int index,
            BytecodeVMConfig config,
            SuperInstructionRegistry registry)
    {
        int maxLength = maxFusableLength(block, index, config.superInstructionCombineMax);
        if (maxLength < config.superInstructionCombineMin)
        {
            return null;
        }
        int length = RandomUtils.randomInt(config.superInstructionCombineMin, maxLength);
        SuperInstructionRegistry.Recipe recipe = registry.register(sequence(block, index, length));
        return recipe == null ? null : new FusedRange(length, recipe);
    }

    private static int maxFusableLength(List<VMInstruction> block, int index, int maxConfigured)
    {
        int maxLength = Math.min(maxConfigured, block.size() - index);
        int length = 0;
        while (length < maxLength && isFusable(block.get(index + length)))
        {
            length++;
        }
        return length;
    }

    private static VMInstruction createSuperInstruction(
            List<VMInstruction> block,
            int index,
            int length,
            SuperInstructionRegistry.Recipe recipe,
            OpcMutator mutator)
    {
        List<VMInstruction> fused = List.copyOf(block.subList(index, index + length));
        return new SuperVMInstruction(
                fused.getFirst().programCounter,
                fused.getLast().nextProgramCounter,
                mutator.toMutated(Opcs.SUPER_INSTRUCTION),
                recipe.id(),
                fused);
    }

    private static List<Opcs> sequence(List<VMInstruction> block, int index, int length)
    {
        List<Opcs> sequence = new ArrayList<>(length);
        for (int offset = 0; offset < length; offset++)
        {
            sequence.add(block.get(index + offset).opcode);
        }
        return List.copyOf(sequence);
    }

    private static List<List<VMInstruction>> splitSafeBlocks(VMMethod method, List<VMInstruction> instructions)
    {
        if (instructions.isEmpty())
        {
            return List.of();
        }

        Set<Integer> instructionPcs = new HashSet<>();
        for (VMInstruction instruction : instructions)
        {
            instructionPcs.add(instruction.programCounter);
        }

        Set<Integer> leaders = new HashSet<>();
        leaders.addAll(method.getControlFlowLeaders());
        leaders.add(instructions.getFirst().programCounter);
        for (int index = 0; index < method.exceptionHandlers.length; index += ProtectedVMMethod.HANDLER_SIZE)
        {
            addLeaderIfPresent(leaders, instructionPcs, method.exceptionHandlers[index]);
            addLeaderIfPresent(leaders, instructionPcs, method.exceptionHandlers[index + 1]);
            addLeaderIfPresent(leaders, instructionPcs, method.exceptionHandlers[index + 2]);
        }

        for (VMInstruction instruction : instructions)
        {
            for (int operandIndex = 0; operandIndex < instruction.operandCount(); operandIndex++)
            {
                if (isJumpTargetOperand(instruction.opcode, operandIndex))
                {
                    addLeaderIfPresent(leaders, instructionPcs, instruction.operand(operandIndex).rawValue);
                }
            }
            if (endsBlock(instruction.opcode))
            {
                addLeaderIfPresent(leaders, instructionPcs, instruction.nextProgramCounter);
            }
        }

        List<List<VMInstruction>> blocks = new ArrayList<>();
        List<VMInstruction> current = new ArrayList<>();
        for (VMInstruction instruction : instructions)
        {
            if (!current.isEmpty() && leaders.contains(instruction.programCounter))
            {
                blocks.add(List.copyOf(current));
                current.clear();
            }
            current.add(instruction);
            if (endsBlock(instruction.opcode))
            {
                blocks.add(List.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty())
        {
            blocks.add(List.copyOf(current));
        }
        return blocks;
    }

    private static void addLeaderIfPresent(Set<Integer> leaders, Set<Integer> instructionPcs, int pc)
    {
        if (instructionPcs.contains(pc))
        {
            leaders.add(pc);
        }
    }

    private static boolean isFusable(VMInstruction instruction)
    {
        return instruction.opcode != Opcs.REGISTER_OP &&
               instruction.opcode != Opcs.DATA_FLOW_REGION &&
               instruction.opcode != Opcs.SUPER_INSTRUCTION;
//        return switch (instruction.opcode)
//        {
//            case NOP, ACONST_NULL,
//                 ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
//                 LCONST_0, LCONST_1,
//                 FCONST_0, FCONST_1, FCONST_2,
//                 DCONST_0, DCONST_1,
//                 BIPUSH, SIPUSH,
//                 ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
//                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
//                 POP, POP2,
//                 DUP, DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2,
//                 SWAP,
//                 IADD, LADD, FADD, DADD,
//                 ISUB, LSUB, FSUB, DSUB,
//                 IMUL, LMUL, FMUL, DMUL,
//                 INEG, LNEG, FNEG, DNEG,
//                 ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR,
//                 IAND, LAND, IOR, LOR, IXOR, LXOR,
//                 IINC,
//                 I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S,
//                 LCMP, FCMPL, FCMPG, DCMPL, DCMPG,
//
//                 LDC,
//                 IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
//                 IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE,
//                 ARRAYLENGTH,
//                 CHECKCAST,
//                 INSTANCEOF,
//                 NEWARRAY,
//                 ANEWARRAY,
//
//                 GETFIELD,
//                 PUTFIELD,
//                 GETSTATIC,
//                 PUTSTATIC,
//                 NEW,
//                 MULTIANEWARRAY,
//
//                 IDIV, LDIV, FDIV, DDIV,
//                 IREM, LREM, FREM, DREM,
//                 IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
//                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
//                 IF_ACMPEQ, IF_ACMPNE,
//                 IFNULL, IFNONNULL,
//                 GOTO,
//                 TABLESWITCH,
//                 LOOKUPSWITCH,
//                 IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN,
//                 ATHROW,
//                 MONITORENTER, MONITOREXIT,
//                 INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE, INVOKEDYNAMIC -> true;
//            default -> false;
//        };
    }

    private static boolean endsBlock(Opcs opcode)
    {
        return switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL,
                 GOTO, TABLESWITCH, LOOKUPSWITCH,
                 IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW -> true;
            default -> false;
        };
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

    private static String signature(List<Opcs> sequence)
    {
        StringBuilder result = new StringBuilder();
        for (Opcs opcode : sequence)
        {
            if (!result.isEmpty())
            {
                result.append(',');
            }
            result.append(opcode.name());
        }
        return result.toString();
    }

    private record FusedRange(int length, SuperInstructionRegistry.Recipe recipe)
    {
    }
}
