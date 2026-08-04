package nhcm.bytecodevm.generator.virtualization.structure;

import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.data.vminsn.VMOperand;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.utils.RandomUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lowers stack operations into explicit register operands and dependency regions. */
public final class LoweredInstructionPlanner
{
    public static final int NO_REGISTER = Integer.MAX_VALUE;
    public static final int REGISTER_PLAN_SIZE = 8;
    public static final int DATA_FLOW_HEADER_SIZE = 2;
    public static final int DATA_FLOW_NODE_SIZE = REGISTER_PLAN_SIZE + 1;

    private LoweredInstructionPlanner()
    {
    }

    public static List<VMInstruction> lowerRegister(VMMethod method, OpcMutator mutator)
    {
        List<VMInstruction> result = new ArrayList<>();
        for (VMInstruction instruction : method.getInstructions())
        {
            RegisterPlan plan = plan(instruction, false);
            result.add(plan == null
                    ? instruction
                    : registerInstruction(instruction, plan, mutator));
        }
        return List.copyOf(result);
    }

    public static List<VMInstruction> lowerDataFlow(VMMethod method, OpcMutator mutator, int maxNodes)
    {
        List<VMInstruction> instructions = method.getInstructions();
        if (instructions.isEmpty())
        {
            return instructions;
        }

        int nodeLimit = Math.max(1, Math.min(30, maxNodes));
        Set<Integer> leaders = findLeaders(method, instructions);
        List<VMInstruction> result = new ArrayList<>();
        int index = 0;
        while (index < instructions.size())
        {
            VMInstruction first = instructions.get(index);
            RegisterPlan firstPlan = plan(first, true);
            if (firstPlan == null)
            {
                result.add(first);
                index++;
                continue;
            }

            List<RegisterPlan> plans = new ArrayList<>();
            List<VMInstruction> source = new ArrayList<>();
            int cursor = index;
            int baseDelta = 0;
            while (cursor < instructions.size() && plans.size() < nodeLimit)
            {
                VMInstruction candidate = instructions.get(cursor);
                if (cursor > index && leaders.contains(candidate.programCounter))
                {
                    break;
                }
                RegisterPlan candidatePlan = plan(candidate, true);
                if (candidatePlan == null)
                {
                    break;
                }
                plans.add(candidatePlan.withBaseDelta(baseDelta));
                source.add(candidate);
                baseDelta += candidatePlan.stackDelta;
                cursor++;
            }

            result.add(dataFlowInstruction(source, plans, baseDelta, mutator));
            index = cursor;
        }
        return List.copyOf(result);
    }

    public static int stackToken(int offset)
    {
        int zigZag = offset << 1 ^ offset >> 31;
        return Integer.MIN_VALUE | zigZag;
    }

    private static VMInstruction registerInstruction(
            VMInstruction source,
            RegisterPlan plan,
            OpcMutator mutator)
    {
        List<VMOperand> operands = new ArrayList<>(REGISTER_PLAN_SIZE);
        addPlan(operands, plan);
        return VMInstruction.synthetic(
                source.programCounter,
                source.nextProgramCounter,
                mutator.toMutated(Opcs.REGISTER_OP),
                Opcs.REGISTER_OP,
                operands);
    }

    private static VMInstruction dataFlowInstruction(
            List<VMInstruction> source,
            List<RegisterPlan> plans,
            int finalStackDelta,
            OpcMutator mutator)
    {
        List<Node> nodes = dependencyNodes(plans);
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++)
        {
            order.add(index);
        }
        RandomUtils.shuffle(order);

        int[] newIndexByOld = new int[nodes.size()];
        for (int newIndex = 0; newIndex < order.size(); newIndex++)
        {
            newIndexByOld[order.get(newIndex)] = newIndex;
        }

        List<VMOperand> operands = new ArrayList<>(
                DATA_FLOW_HEADER_SIZE + nodes.size() * DATA_FLOW_NODE_SIZE);
        operands.add(VMOperand.immediate(0, nodes.size()));
        operands.add(VMOperand.immediate(1, finalStackDelta));
        for (int oldIndex : order)
        {
            Node node = nodes.get(oldIndex);
            addPlan(operands, node.plan);
            int remappedDependencies = 0;
            for (int dependency = 0; dependency < nodes.size(); dependency++)
            {
                if ((node.dependencies & 1 << dependency) != 0)
                {
                    remappedDependencies |= 1 << newIndexByOld[dependency];
                }
            }
            operands.add(VMOperand.immediate(operands.size(), remappedDependencies));
        }

        return VMInstruction.synthetic(
                source.getFirst().programCounter,
                source.getLast().nextProgramCounter,
                mutator.toMutated(Opcs.DATA_FLOW_REGION),
                Opcs.DATA_FLOW_REGION,
                operands);
    }

    private static void addPlan(List<VMOperand> output, RegisterPlan plan)
    {
        output.add(VMOperand.immediate(output.size(), plan.opcode.ordinal()));
        output.add(VMOperand.immediate(output.size(), plan.destination));
        output.add(VMOperand.immediate(output.size(), plan.sourceA));
        output.add(VMOperand.immediate(output.size(), plan.sourceB));
        output.add(plan.auxiliary == null
                ? VMOperand.immediate(output.size(), 0)
                : plan.auxiliary.reindex(output.size()));
        output.add(VMOperand.immediate(output.size(), plan.stackDelta));
        output.add(VMOperand.immediate(output.size(), plan.width));
        output.add(VMOperand.immediate(output.size(), plan.baseDelta));
    }

    private static List<Node> dependencyNodes(List<RegisterPlan> plans)
    {
        Map<Integer, Integer> lastWriter = new HashMap<>();
        Map<Integer, Integer> readers = new HashMap<>();
        List<Node> nodes = new ArrayList<>(plans.size());
        for (int index = 0; index < plans.size(); index++)
        {
            RegisterPlan plan = plans.get(index);
            int dependencies = 0;
            Set<Integer> sources = new LinkedHashSet<>();
            addPhysicalRegister(sources, plan.sourceA, plan.baseDelta);
            addPhysicalRegister(sources, plan.sourceB, plan.baseDelta);
            for (int source : sources)
            {
                dependencies |= bit(lastWriter.get(source));
            }

            Integer destination = physicalRegister(plan.destination, plan.baseDelta);
            if (destination != null)
            {
                dependencies |= bit(lastWriter.get(destination));
                dependencies |= readers.getOrDefault(destination, 0);
            }
            nodes.add(new Node(plan, dependencies));

            for (int source : sources)
            {
                readers.merge(source, 1 << index, (left, right) -> left | right);
            }
            if (destination != null)
            {
                lastWriter.put(destination, index);
                readers.remove(destination);
            }
        }
        return nodes;
    }

    private static int bit(Integer index)
    {
        return index == null ? 0 : 1 << index;
    }

    private static void addPhysicalRegister(Set<Integer> output, int token, int baseDelta)
    {
        Integer register = physicalRegister(token, baseDelta);
        if (register != null)
        {
            output.add(register);
        }
    }

    private static Integer physicalRegister(int token, int baseDelta)
    {
        if (token == NO_REGISTER)
        {
            return null;
        }
        if (token >= 0)
        {
            return token;
        }
        int zigZag = token & Integer.MAX_VALUE;
        int offset = zigZag >>> 1 ^ -(zigZag & 1);
        return Integer.MIN_VALUE | baseDelta + offset & Integer.MAX_VALUE;
    }

    private static RegisterPlan plan(VMInstruction instruction, boolean dataFlowSafe)
    {
        Opcs opcode = instruction.opcode;
        if (dataFlowSafe && !isDataFlowSafe(opcode))
        {
            return null;
        }

        return switch (opcode)
        {
            case NOP -> simple(opcode, NO_REGISTER, NO_REGISTER, NO_REGISTER, null, 0, 0);
            case ACONST_NULL -> simple(opcode, stackToken(0), NO_REGISTER, NO_REGISTER, null, 1, 1);
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 BIPUSH, SIPUSH -> simple(opcode, stackToken(0), NO_REGISTER, NO_REGISTER,
                    opcode.hasOperand ? instruction.operand(0) : null, 1, 1);
            case LCONST_0, LCONST_1 -> simple(opcode, stackToken(0), NO_REGISTER, NO_REGISTER, null, 1, 2);
            case FCONST_0, FCONST_1, FCONST_2 -> simple(opcode, stackToken(0), NO_REGISTER, NO_REGISTER, null, 1, 1);
            case DCONST_0, DCONST_1 -> simple(opcode, stackToken(0), NO_REGISTER, NO_REGISTER, null, 1, 2);
            case LDC -> simple(opcode, stackToken(0), NO_REGISTER, NO_REGISTER, instruction.operand(0), 1,
                    constantWidth(instruction.operand(0).value));
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> simple(
                    opcode, stackToken(0), instruction.operand(0).asInt(), NO_REGISTER, null, 1,
                    opcode == Opcs.LLOAD || opcode == Opcs.DLOAD ? 2 : 1);
            case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> simple(
                    opcode, instruction.operand(0).asInt(), stackToken(-1), NO_REGISTER, null, -1, 0);
            case POP -> simple(opcode, NO_REGISTER, stackToken(-1), NO_REGISTER, null, -1, 0);
            case IADD, LADD, FADD, DADD,
                 ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL,
                 IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM,
                 IAND, LAND, IOR, LOR, IXOR, LXOR -> binary(opcode, numericWidth(opcode));
            case ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR -> binary(opcode, opcode.name().charAt(0) == 'L' ? 2 : 1);
            case INEG, LNEG, FNEG, DNEG -> unary(opcode, numericWidth(opcode));
            case IINC -> simple(
                    opcode,
                    instruction.operand(0).asInt(),
                    instruction.operand(0).asInt(),
                    NO_REGISTER,
                    instruction.operand(1),
                    0,
                    1);
            case I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D,
                 D2I, D2L, D2F, I2B, I2C, I2S -> unary(opcode, conversionWidth(opcode));
            case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> binary(opcode, 1);
            default -> null;
        };
    }

    private static RegisterPlan binary(Opcs opcode, int width)
    {
        return simple(opcode, stackToken(-2), stackToken(-2), stackToken(-1), null, -1, width);
    }

    private static RegisterPlan unary(Opcs opcode, int width)
    {
        return simple(opcode, stackToken(-1), stackToken(-1), NO_REGISTER, null, 0, width);
    }

    private static RegisterPlan simple(
            Opcs opcode,
            int destination,
            int sourceA,
            int sourceB,
            VMOperand auxiliary,
            int stackDelta,
            int width)
    {
        return new RegisterPlan(opcode, destination, sourceA, sourceB, auxiliary, stackDelta, width, 0);
    }

    private static boolean isDataFlowSafe(Opcs opcode)
    {
        return switch (opcode)
        {
            case NOP, ACONST_NULL,
                 ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1,
                 BIPUSH, SIPUSH,
                 ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
                 POP,
                 IADD, LADD, FADD, DADD,
                 ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL,
                 FREM, DREM, FDIV, DDIV,
                 INEG, LNEG, FNEG, DNEG,
                 ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR,
                 IAND, LAND, IOR, LOR, IXOR, LXOR,
                 IINC,
                 I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D,
                 D2I, D2L, D2F, I2B, I2C, I2S,
                 LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> true;
            default -> false;
        };
    }

    private static int numericWidth(Opcs opcode)
    {
        char type = opcode.name().charAt(0);
        return type == 'L' || type == 'D' ? 2 : 1;
    }

    private static int conversionWidth(Opcs opcode)
    {
        char target = opcode.name().charAt(2);
        return target == 'L' || target == 'D' ? 2 : 1;
    }

    private static int constantWidth(Object value)
    {
        return value instanceof Long || value instanceof Double ? 2 : 1;
    }

    private static Set<Integer> findLeaders(VMMethod method, List<VMInstruction> instructions)
    {
        Set<Integer> pcs = new HashSet<>();
        for (VMInstruction instruction : instructions)
        {
            pcs.add(instruction.programCounter);
        }
        Set<Integer> leaders = new HashSet<>();
        leaders.addAll(method.getControlFlowLeaders());
        leaders.add(instructions.getFirst().programCounter);
        for (int index = 0; index < method.exceptionHandlers.length; index += 4)
        {
            addLeader(leaders, pcs, method.exceptionHandlers[index]);
            addLeader(leaders, pcs, method.exceptionHandlers[index + 1]);
            addLeader(leaders, pcs, method.exceptionHandlers[index + 2]);
        }
        for (VMInstruction instruction : instructions)
        {
            for (int operand = 0; operand < instruction.operandCount(); operand++)
            {
                if (isJumpTarget(instruction.opcode, operand))
                {
                    addLeader(leaders, pcs, instruction.operand(operand).rawValue);
                }
            }
            if (endsBlock(instruction.opcode))
            {
                addLeader(leaders, pcs, instruction.nextProgramCounter);
            }
        }
        return leaders;
    }

    private static void addLeader(Set<Integer> leaders, Set<Integer> pcs, int pc)
    {
        if (pcs.contains(pc))
        {
            leaders.add(pc);
        }
    }

    private static boolean isJumpTarget(Opcs opcode, int operand)
    {
        return switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL, GOTO -> operand == 0;
            case TABLESWITCH -> operand == 2 || operand >= 4;
            case LOOKUPSWITCH -> operand == 0 || operand >= 3 && (operand & 1) == 1;
            default -> false;
        };
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

    private record RegisterPlan(
            Opcs opcode,
            int destination,
            int sourceA,
            int sourceB,
            VMOperand auxiliary,
            int stackDelta,
            int width,
            int baseDelta)
    {
        private RegisterPlan withBaseDelta(int value)
        {
            return new RegisterPlan(opcode, destination, sourceA, sourceB, auxiliary, stackDelta, width, value);
        }
    }

    private record Node(RegisterPlan plan, int dependencies)
    {
    }
}
