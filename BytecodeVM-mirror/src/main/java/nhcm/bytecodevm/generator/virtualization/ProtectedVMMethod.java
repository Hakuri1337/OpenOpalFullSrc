package nhcm.bytecodevm.generator.virtualization;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.data.vminsn.VMOperand;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionCombiner;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionRegistry;
import nhcm.bytecodevm.generator.virtualization.structure.LoweredInstructionPlanner;
import nhcm.bytecodevm.generator.virtualization.structure.VMStructurePlan;
import nhcm.bytecodevm.utils.RandomUtils;

import java.util.*;

public class ProtectedVMMethod
{
    public static final int CONSTANT_STRING = 1;
    public static final int CONSTANT_INTEGER = 2;
    public static final int CONSTANT_LONG = 3;
    public static final int CONSTANT_FLOAT = 4;
    public static final int CONSTANT_DOUBLE = 5;
    public static final int CONSTANT_TYPE = 6;

    public static final int RECORD_SIZE = 8;
    public static final int BLOCK_SIZE = 4;
    public static final int HANDLER_SIZE = 4;

    public static final int LAYOUT_PC = 0;
    public static final int LAYOUT_ORIGINAL_PC = 1;
    public static final int LAYOUT_NEXT_PC = 2;
    public static final int LAYOUT_OPERAND_START = 3;
    public static final int LAYOUT_OPERAND_COUNT = 4;
    public static final int LAYOUT_CONSTANT_MASK = 5;
    public static final int LAYOUT_STATE_KEY = 6;
    public static final int LAYOUT_BLOCK_INDEX = 7;

    public static final int BLOCK_START_PC = 0;
    public static final int BLOCK_ORIGINAL_START_PC = 1;
    public static final int BLOCK_START_SLOT = 2;
    public static final int BLOCK_SLOT_COUNT = 3;

    public static final int FEATURE_PER_METHOD_OPCODE_MAP = 1;
    public static final int FEATURE_ENCRYPT_OPERANDS = 1 << 1;
    public static final int FEATURE_BIND_CONSTANTS = 1 << 2;
    public static final int FEATURE_OBFUSCATE_DISPATCH = 1 << 3;

    public final int[] opcodeStream;
    public final int[] operandStream;
    public final int[] layoutStream;
    public final int[] blockStream;
    public final Object[] constants;
    public final int[] exceptionHandlers;
    public final int[] opcodeMap;
    public final int methodKey;
    public final int featureFlags;
    public final boolean usesSuperInstructions;

    private ProtectedVMMethod(
            int[] opcodeStream,
            int[] operandStream,
            int[] layoutStream,
            int[] blockStream,
            Object[] constants,
            int[] exceptionHandlers,
            int[] opcodeMap,
            int methodKey,
            int featureFlags,
            boolean usesSuperInstructions)
    {
        this.opcodeStream = opcodeStream;
        this.operandStream = operandStream;
        this.layoutStream = layoutStream;
        this.blockStream = blockStream;
        this.constants = constants;
        this.exceptionHandlers = exceptionHandlers;
        this.opcodeMap = opcodeMap;
        this.methodKey = methodKey;
        this.featureFlags = featureFlags;
        this.usesSuperInstructions = usesSuperInstructions;
    }

    public static ProtectedVMMethod from(CompiledMethod compiledMethod, BytecodeVMConfig config)
    {
        return from(compiledMethod, config, VMObfProfile.random());
    }

    public static ProtectedVMMethod from(
            CompiledMethod compiledMethod,
            BytecodeVMConfig config,
            VMObfProfile profile)
    {
        return from(compiledMethod, config, profile, new SuperInstructionRegistry(config.superInstructionMaxHandlers));
    }

    public static ProtectedVMMethod from(
            CompiledMethod compiledMethod,
            BytecodeVMConfig config,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions)
    {
        VMMethod method = compiledMethod.vmMethod;
        List<VMInstruction> loweredInstructions = switch (config.vmStructure)
        {
            case REGISTER_BASED -> LoweredInstructionPlanner.lowerRegister(
                    method,
                    method.getOpcMutator());
            case DATA_FLOW -> LoweredInstructionPlanner.lowerDataFlow(
                    method,
                    method.getOpcMutator(),
                    VMStructurePlan.forStructure(config.vmStructure).laneCount());
            default -> method.getInstructions();
        };
        List<VMInstruction> instructions = SuperInstructionCombiner.combine(
                method,
                loweredInstructions,
                config,
                superInstructions,
                method.getOpcMutator());
        boolean usesSuperInstructions = instructions.stream().anyMatch(instruction -> instruction.opcode == Opcs.SUPER_INSTRUCTION);
        boolean protect = config.protectCodePool;
        int featureFlags = featureFlags(config);
        boolean dynamicStateKey = protect && config.dynamicStateKey;
        boolean virtualizeInstructionAddresses = protect &&
                                                config.virtualizeInstructionAddresses &&
                                                compiledMethod.virtualizeInstructionAddresses;
        int methodKey = protect ? nonZeroRandom() : 0;

        Map<Integer, Integer> virtualPcByOriginalPc = createVirtualPcMap(instructions, virtualizeInstructionAddresses);
        OpcodeLayout opcodeLayout = createOpcodeLayout(instructions, config, methodKey, profile);
        List<BasicBlock> blocks = createBlocks(method, instructions, virtualPcByOriginalPc, virtualizeInstructionAddresses, config);
        List<VMInstruction> records = createRecords(blocks, protect && config.shuffleInstructionBlocks);

        Map<VMInstruction, Integer> slotByInstruction = new IdentityHashMap<>();
        Map<VMInstruction, Integer> blockByInstruction = new IdentityHashMap<>();
        Map<VMInstruction, Integer> stateKeyByInstruction = new IdentityHashMap<>();
        for (int slot = 0; slot < records.size(); slot++)
        {
            slotByInstruction.put(records.get(slot), slot);
        }
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++)
        {
            for (VMInstruction instruction : blocks.get(blockIndex).instructions)
            {
                blockByInstruction.put(instruction, blockIndex);
                stateKeyByInstruction.put(instruction, dynamicStateKey ? nonZeroRandom() : 0);
            }
        }

        ConstantLayout constantLayout = createConstantLayout(
                method,
                records,
                slotByInstruction,
                stateKeyByInstruction,
                blockByInstruction,
                virtualPcByOriginalPc,
                methodKey,
                config,
                profile);

        int[] operandStarts = createOperandStarts(records, protect && config.splitCodeStreams);
        int totalOperands = 0;
        for (VMInstruction instruction : records)
        {
            totalOperands += instruction.operandCount();
        }

        int[] opcodeStream = new int[records.size()];
        int[] operandStream = new int[totalOperands];
        int[] layoutStream = new int[records.size() * RECORD_SIZE];
        int[] blockStream = createBlockStream(blocks, methodKey, protect, profile);
        int[] firstSlotByBlock = new int[blocks.size()];
        int firstSlotCursor = 0;
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++)
        {
            firstSlotByBlock[blockIndex] = firstSlotCursor;
            firstSlotCursor += blocks.get(blockIndex).instructions.size();
        }

        for (VMInstruction instruction : records)
        {
            int slot = slotByInstruction.get(instruction);
            int blockIndex = blockByInstruction.get(instruction);
            int stateKey = stateKeyByInstruction.get(instruction);
            int virtualPc = virtualPcByOriginalPc.get(instruction.programCounter);
            int nextPc = instruction.nextProgramCounter >= method.methodEndPc
                    ? -1
                    : remapProgramCounter(instruction.nextProgramCounter, method, virtualPcByOriginalPc, virtualizeInstructionAddresses);
            int operandStart = operandStarts[slot];
            int operandCount = instruction.operandCount();
            int constantMask = protect && config.bindConstantsToOperands
                    ? constantMask(instruction)
                    : 0;

            int dispatchOpcode = dispatchOpcode(instruction.mutatedOpcode, config, profile);
            int virtualOpcode = opcodeLayout.virtualByRealOpcode.get(dispatchOpcode);
            opcodeStream[slot] = protect && config.perMethodOpcodeMap
                    ? virtualOpcode ^ profile.opcodeMix(methodKey, stateKey, virtualPc, slot)
                    : virtualOpcode;

            setLayout(layoutStream, slot, LAYOUT_PC, virtualPc, methodKey, stateKey, protect, profile);
            setLayout(layoutStream, slot, LAYOUT_ORIGINAL_PC, instruction.programCounter, methodKey, stateKey, protect, profile);
            setLayout(layoutStream, slot, LAYOUT_NEXT_PC, nextPc, methodKey, stateKey, protect, profile);
            setLayout(layoutStream, slot, LAYOUT_OPERAND_START, operandStart, methodKey, stateKey, protect, profile);
            setLayout(layoutStream, slot, LAYOUT_OPERAND_COUNT, operandCount, methodKey, stateKey, protect, profile);
            setLayout(layoutStream, slot, LAYOUT_CONSTANT_MASK, constantMask, methodKey, stateKey, protect, profile);
            int firstSlot = firstSlotByBlock[blockIndex];
            int previousStateKey = slot == firstSlot
                    ? 0
                    : stateKeyByInstruction.get(records.get(slot - 1));
            setLayoutStateKey(
                    layoutStream,
                    slot,
                    stateKey,
                    previousStateKey,
                    blockIndex,
                    slot == firstSlot,
                    methodKey,
                    protect,
                    profile);
            setLayout(layoutStream, slot, LAYOUT_BLOCK_INDEX, blockIndex, methodKey, stateKey, protect, profile);

            for (int operandIndex = 0; operandIndex < operandCount; operandIndex++)
            {
                VMOperand operand = instruction.operand(operandIndex);
                int value = remapOperand(
                        instruction,
                        operand,
                        operandIndex,
                        virtualPcByOriginalPc,
                        constantLayout.indexByOriginal,
                        config,
                        virtualizeInstructionAddresses);
                if (protect && config.bindConstantsToOperands && operand.constantReference)
                {
                    value ^= profile.constantMix(methodKey, stateKey, dispatchOpcode, virtualPc, operandIndex);
                }
                if (protect && config.encryptOperands)
                {
                    value ^= profile.operandMix(methodKey, stateKey, dispatchOpcode, virtualPc, operandIndex, operandStart + operandIndex);
                }
                operandStream[operandStart + operandIndex] = value;
            }
        }

        return new ProtectedVMMethod(
                opcodeStream,
                operandStream,
                layoutStream,
                blockStream,
                constantLayout.constants,
                protectExceptionHandlers(
                        method,
                        constantLayout.indexByOriginal,
                        virtualPcByOriginalPc,
                        methodKey,
                        protect,
                        virtualizeInstructionAddresses,
                        profile),
                opcodeLayout.encodedOpcodeMap,
                methodKey,
                featureFlags,
                usesSuperInstructions);
    }

    private static int featureFlags(BytecodeVMConfig config)
    {
        int flags = 0;
        if (config.perMethodOpcodeMap)
        {
            flags |= FEATURE_PER_METHOD_OPCODE_MAP;
        }
        if (config.encryptOperands)
        {
            flags |= FEATURE_ENCRYPT_OPERANDS;
        }
        if (config.bindConstantsToOperands)
        {
            flags |= FEATURE_BIND_CONSTANTS;
        }
        if (config.obfuscateDispatch)
        {
            flags |= FEATURE_OBFUSCATE_DISPATCH;
        }
        return flags;
    }

    private static int[] protectExceptionHandlers(
            VMMethod method,
            Map<Integer, Integer> constantIndexByOriginal,
            Map<Integer, Integer> virtualPcByOriginalPc,
            int methodKey,
            boolean protect,
            boolean virtualizeInstructionAddresses,
            VMObfProfile profile)
    {
        int[] handlers = new int[method.exceptionHandlers.length];
        for (int index = 0; index < method.exceptionHandlers.length; index += HANDLER_SIZE)
        {
            int handlerSlot = index / HANDLER_SIZE;
            int startPc = method.exceptionHandlers[index];
            int endPc = method.exceptionHandlers[index + 1];
            int handlerPc = remapProgramCounter(
                    method.exceptionHandlers[index + 2],
                    method,
                    virtualPcByOriginalPc,
                    virtualizeInstructionAddresses);
            int typeIndex = method.exceptionHandlers[index + 3] < 0
                    ? -1
                    : constantIndexByOriginal.get(method.exceptionHandlers[index + 3]);
            handlers[index] = protect ? startPc ^ profile.handlerMix(methodKey, handlerSlot, 0) : startPc;
            handlers[index + 1] = protect ? endPc ^ profile.handlerMix(methodKey, handlerSlot, 1) : endPc;
            handlers[index + 2] = protect ? handlerPc ^ profile.handlerMix(methodKey, handlerSlot, 2) : handlerPc;
            handlers[index + 3] = protect ? typeIndex ^ profile.handlerMix(methodKey, handlerSlot, 3) : typeIndex;
        }
        return handlers;
    }

    private static Map<Integer, Integer> createVirtualPcMap(
            List<VMInstruction> instructions,
            boolean virtualize)
    {
        Map<Integer, Integer> virtualByOriginal = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (VMInstruction instruction : instructions)
        {
            int virtualPc = virtualize ? nonZeroRandom() : instruction.programCounter;
            while (!used.add(virtualPc))
            {
                virtualPc = nonZeroRandom();
            }
            virtualByOriginal.put(instruction.programCounter, virtualPc);
        }
        return virtualByOriginal;
    }

    private static ConstantLayout createConstantLayout(
            VMMethod method,
            List<VMInstruction> instructions,
            Map<VMInstruction, Integer> slotByInstruction,
            Map<VMInstruction, Integer> stateKeyByInstruction,
            Map<VMInstruction, Integer> blockByInstruction,
            Map<Integer, Integer> virtualPcByOriginalPc,
            int methodKey,
            BytecodeVMConfig config,
            VMObfProfile profile)
    {
        Object[] constants = method.constants;
        Map<Integer, Set<ConstantBinding>> bindingsByConstant = new HashMap<>();
        if (config.protectCodePool && config.dynamicConstantDecrypt)
        {
            for (VMInstruction instruction : instructions)
            {
                int slot = slotByInstruction.get(instruction);
                int stateKey = stateKeyByInstruction.get(instruction);
                int virtualPc = virtualPcByOriginalPc.get(instruction.programCounter);
                int blockIndex = blockByInstruction.get(instruction);
                int opcode = dispatchOpcode(instruction.mutatedOpcode, config, profile);
                ConstantBinding binding = new ConstantBinding(
                        profile.constantStateMix(
                                methodKey,
                                stateKey,
                                virtualPc,
                                blockIndex,
                                slot,
                                opcode),
                        profile.constantStateMixSecondary(
                                methodKey,
                                stateKey,
                                virtualPc,
                                blockIndex,
                                slot,
                                opcode));
                for (int operandIndex = 0; operandIndex < instruction.operandCount(); operandIndex++)
                {
                    VMOperand operand = instruction.operand(operandIndex);
                    if (operand.constantReference)
                    {
                        bindingsByConstant.computeIfAbsent(operand.rawValue, ignored -> new LinkedHashSet<>()).add(binding);
                    }
                }
            }

            for (int handler = 0; handler < method.exceptionHandlers.length; handler += HANDLER_SIZE)
            {
                int typeIndex = method.exceptionHandlers[handler + 3];
                if (typeIndex < 0)
                {
                    continue;
                }
                int startPc = method.exceptionHandlers[handler];
                int endPc = method.exceptionHandlers[handler + 1];
                Set<ConstantBinding> bindings = bindingsByConstant.computeIfAbsent(typeIndex, ignored -> new LinkedHashSet<>());
                for (VMInstruction instruction : instructions)
                {
                    if (instruction.programCounter >= startPc && instruction.programCounter < endPc)
                    {
                        int slot = slotByInstruction.get(instruction);
                        int stateKey = stateKeyByInstruction.get(instruction);
                        int virtualPc = virtualPcByOriginalPc.get(instruction.programCounter);
                        int blockIndex = blockByInstruction.get(instruction);
                        int opcode = dispatchOpcode(instruction.mutatedOpcode, config, profile);
                        bindings.add(new ConstantBinding(
                                profile.constantStateMix(
                                        methodKey,
                                        stateKey,
                                        virtualPc,
                                        blockIndex,
                                        slot,
                                        opcode),
                                profile.constantStateMixSecondary(
                                        methodKey,
                                        stateKey,
                                        virtualPc,
                                        blockIndex,
                                        slot,
                                        opcode)));
                    }
                }
            }
        }

        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < constants.length; index++)
        {
            order.add(index);
        }
        if (config.protectCodePool && config.shuffleConstants)
        {
            RandomUtils.shuffle(order);
        }

        Object[] shuffled = new Object[constants.length];
        Map<Integer, Integer> indexByOriginal = new HashMap<>();
        for (int newIndex = 0; newIndex < order.size(); newIndex++)
        {
            int originalIndex = order.get(newIndex);
            Set<ConstantBinding> bindings = bindingsByConstant.get(originalIndex);
            if (config.protectCodePool && config.dynamicConstantDecrypt && (bindings == null || bindings.isEmpty()))
            {
                bindings = Set.of(new ConstantBinding(
                        profile.constantStateMix(methodKey, 0, 0, -1, -1, 0),
                        profile.constantStateMixSecondary(methodKey, 0, 0, -1, -1, 0)));
            }
            shuffled[newIndex] = protectConstant(constants[originalIndex], bindings, config, profile);
            indexByOriginal.put(originalIndex, newIndex);
        }
        return new ConstantLayout(shuffled, indexByOriginal);
    }

    private static Object protectConstant(
            Object value,
            Set<ConstantBinding> bindings,
            BytecodeVMConfig config,
            VMObfProfile profile)
    {
        if (!config.protectCodePool || !config.dynamicConstantDecrypt)
        {
            return value;
        }
        int[] plain = switch (value)
        {
            case String string -> constantPayload(CONSTANT_STRING, string);
            case Integer integer -> new int[]{CONSTANT_INTEGER, integer};
            case Long number -> new int[]{CONSTANT_LONG, (int) (number >>> 32), number.intValue()};
            case Float number -> new int[]{CONSTANT_FLOAT, Float.floatToRawIntBits(number)};
            case Double number -> {
                long bits = Double.doubleToRawLongBits(number);
                yield new int[]{CONSTANT_DOUBLE, (int) (bits >>> 32), (int) bits};
            }
            case org.objectweb.asm.Type type -> constantPayload(CONSTANT_TYPE, type.getDescriptor());
            default -> null;
        };
        if (plain == null)
        {
            return value;
        }

        int[][] variants = new int[bindings.size()][];
        int variantIndex = 0;
        for (ConstantBinding binding : bindings)
        {
            int nonceA = nonZeroRandom();
            int nonceB = nonZeroRandom();
            int[] variant = new int[plain.length + 4];
            variant[0] = nonceA;
            variant[1] = nonceB;
            variant[2] = profile.constantSelector(binding.primary, binding.secondary, nonceA, nonceB);
            variant[3] = profile.constantSelectorSecondary(binding.primary, binding.secondary, nonceA, nonceB);
            for (int index = 0; index < plain.length; index++)
            {
                variant[index + 4] = plain[index] ^ profile.constantStream(
                        binding.primary,
                        binding.secondary,
                        nonceA,
                        nonceB,
                        index);
            }
            variants[variantIndex++] = variant;
        }
        for (int current = variants.length - 1; current > 0; current--)
        {
            int swap = RandomUtils.randomInt(current + 1);
            int[] temporary = variants[current];
            variants[current] = variants[swap];
            variants[swap] = temporary;
        }
        return new EncodedConstant(variants);
    }

    private static int[] constantPayload(int tag, String value)
    {
        int[] payload = new int[value.length() + 1];
        payload[0] = tag;
        for (int index = 0; index < value.length(); index++)
        {
            payload[index + 1] = value.charAt(index);
        }
        return payload;
    }

    private static OpcodeLayout createOpcodeLayout(
            List<VMInstruction> instructions,
            BytecodeVMConfig config,
            int methodKey,
            VMObfProfile profile)
    {
        List<Integer> realOpcodes = new ArrayList<>();
        for (VMInstruction instruction : instructions)
        {
            int dispatchOpcode = dispatchOpcode(instruction.mutatedOpcode, config, profile);
            if (!realOpcodes.contains(dispatchOpcode))
            {
                realOpcodes.add(dispatchOpcode);
            }
        }

        List<Integer> virtualOpcodes = new ArrayList<>();
        for (int index = 0; index < realOpcodes.size(); index++)
        {
            virtualOpcodes.add(index);
        }
        if (config.protectCodePool && config.perMethodOpcodeMap)
        {
            RandomUtils.shuffle(virtualOpcodes);
        }

        Map<Integer, Integer> virtualByReal = new HashMap<>();
        int[] opcodeMap = new int[realOpcodes.size()];
        for (int index = 0; index < realOpcodes.size(); index++)
        {
            int realOpcode = realOpcodes.get(index);
            int virtualOpcode = virtualOpcodes.get(index);
            virtualByReal.put(realOpcode, virtualOpcode);
            opcodeMap[virtualOpcode] = config.protectCodePool && config.perMethodOpcodeMap
                    ? realOpcode ^ profile.opcodeMapMix(methodKey, 0, virtualOpcode)
                    : realOpcode;
        }
        return new OpcodeLayout(virtualByReal, opcodeMap);
    }

    private static int dispatchOpcode(int opcode, BytecodeVMConfig config, VMObfProfile profile)
    {
        return config.vmStructure == VMStructure.THREADED_DIRECT
                ? profile.directHandlerToken(opcode)
                : opcode;
    }

    private static List<BasicBlock> createBlocks(
            VMMethod method,
            List<VMInstruction> instructions,
            Map<Integer, Integer> virtualPcByOriginalPc,
            boolean virtualizeInstructionAddresses,
            BytecodeVMConfig config)
    {
        if (instructions.isEmpty() || !config.protectCodePool || !config.virtualControlFlowGraph)
        {
            return List.of(new BasicBlock(
                    instructions.isEmpty() ? 0 : instructions.getFirst().programCounter,
                    instructions.isEmpty() ? 0 : virtualPcByOriginalPc.get(instructions.getFirst().programCounter),
                    List.copyOf(instructions)));
        }

        Set<Integer> instructionPcs = new HashSet<>();
        for (VMInstruction instruction : instructions)
        {
            instructionPcs.add(instruction.programCounter);
        }

        Set<Integer> leaders = new TreeSet<>();
        leaders.add(instructions.getFirst().programCounter);
        for (int index = 0; index < method.exceptionHandlers.length; index += HANDLER_SIZE)
        {
            addLeaderIfPresent(leaders, instructionPcs, method.exceptionHandlers[index]);
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

        List<BasicBlock> blocks = new ArrayList<>();
        List<VMInstruction> current = new ArrayList<>();
        int currentStartPc = instructions.getFirst().programCounter;
        for (VMInstruction instruction : instructions)
        {
            if (!current.isEmpty() && leaders.contains(instruction.programCounter))
            {
                blocks.add(new BasicBlock(
                        currentStartPc,
                        remapProgramCounter(currentStartPc, method, virtualPcByOriginalPc, virtualizeInstructionAddresses),
                        List.copyOf(current)));
                current.clear();
                currentStartPc = instruction.programCounter;
            }
            current.add(instruction);
        }
        if (!current.isEmpty())
        {
            blocks.add(new BasicBlock(
                    currentStartPc,
                    remapProgramCounter(currentStartPc, method, virtualPcByOriginalPc, virtualizeInstructionAddresses),
                    List.copyOf(current)));
        }

        RandomUtils.shuffle(blocks);
        return List.copyOf(blocks);
    }

    private static List<VMInstruction> createRecords(List<BasicBlock> blocks, boolean shuffleWithinBlocks)
    {
        List<VMInstruction> records = new ArrayList<>();
        for (BasicBlock block : blocks)
        {
            List<VMInstruction> blockInstructions = new ArrayList<>(block.instructions);
            if (shuffleWithinBlocks)
            {
                RandomUtils.shuffle(blockInstructions);
            }
            records.addAll(blockInstructions);
        }
        return List.copyOf(records);
    }

    private static int[] createBlockStream(
            List<BasicBlock> blocks,
            int methodKey,
            boolean protect,
            VMObfProfile profile)
    {
        int[] stream = new int[blocks.size() * BLOCK_SIZE];
        int firstSlot = 0;
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++)
        {
            BasicBlock block = blocks.get(blockIndex);
            setBlock(stream, blockIndex, BLOCK_START_PC, block.virtualStartPc, methodKey, protect, profile);
            setBlock(stream, blockIndex, BLOCK_ORIGINAL_START_PC, block.originalStartPc, methodKey, protect, profile);
            setBlock(stream, blockIndex, BLOCK_START_SLOT, firstSlot, methodKey, protect, profile);
            setBlock(stream, blockIndex, BLOCK_SLOT_COUNT, block.instructions.size(), methodKey, protect, profile);
            firstSlot += block.instructions.size();
        }
        return stream;
    }

    private static void addLeaderIfPresent(Set<Integer> leaders, Set<Integer> instructionPcs, int pc)
    {
        if (instructionPcs.contains(pc))
        {
            leaders.add(pc);
        }
    }

    private static boolean endsBlock(Opcs opcode)
    {
        return switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL,
                 GOTO, TABLESWITCH, LOOKUPSWITCH, IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW -> true;
            default -> false;
        };
    }

    private static int[] createOperandStarts(List<VMInstruction> records, boolean shuffle)
    {
        int[] starts = new int[records.size()];
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < records.size(); slot++)
        {
            slots.add(slot);
        }
        if (shuffle)
        {
            RandomUtils.shuffle(slots);
        }

        int cursor = 0;
        for (int slot : slots)
        {
            starts[slot] = cursor;
            cursor += records.get(slot).operandCount();
        }
        return starts;
    }

    private static int remapOperand(
            VMInstruction instruction,
            VMOperand operand,
            int operandIndex,
            Map<Integer, Integer> virtualPcByOriginalPc,
            Map<Integer, Integer> constantIndexByOriginal,
            BytecodeVMConfig config,
            boolean virtualizeInstructionAddresses)
    {
        int value = operand.rawValue;
        if (isJumpTargetOperand(instruction.opcode, operandIndex))
        {
            if (!virtualizeInstructionAddresses)
            {
                return value;
            }
            Integer virtualTarget = virtualPcByOriginalPc.get(value);
            if (virtualTarget == null)
            {
                throw new IllegalStateException("Jump target has no virtual pc: " + value);
            }
            return virtualTarget;
        }
        if (operand.constantReference)
        {
            Integer remapped = constantIndexByOriginal.get(value);
            if (remapped == null)
            {
                throw new IllegalStateException("Constant operand has no protected index: " + value);
            }
            return remapped;
        }
        return value;
    }

    private static int remapProgramCounter(
            int pc,
            VMMethod method,
            Map<Integer, Integer> virtualPcByOriginalPc,
            boolean virtualizeInstructionAddresses)
    {
        if (pc >= method.methodEndPc)
        {
            return -1;
        }
        if (!virtualizeInstructionAddresses)
        {
            return pc;
        }
        Integer virtualPc = virtualPcByOriginalPc.get(pc);
        if (virtualPc == null)
        {
            throw new IllegalStateException("Program counter has no virtual pc: " + pc);
        }
        return virtualPc;
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

    private static int constantMask(VMInstruction instruction)
    {
        int mask = 0;
        for (int operandIndex = 0; operandIndex < instruction.operandCount(); operandIndex++)
        {
            if (instruction.operand(operandIndex).constantReference && operandIndex < Integer.SIZE)
            {
                mask |= 1 << operandIndex;
            }
        }
        return mask;
    }

    private static void setLayout(
            int[] layout,
            int slot,
            int field,
            int value,
            int methodKey,
            int stateKey,
            boolean protect,
            VMObfProfile profile)
    {
        int physicalField = profile.layoutSlot(field);
        layout[slot * RECORD_SIZE + physicalField] = protect
                ? value ^ profile.layoutMix(methodKey, stateKey, slot, physicalField)
                : value;
    }

    private static void setLayoutStateKey(
            int[] layout,
            int slot,
            int stateKey,
            int previousStateKey,
            int blockIndex,
            boolean blockEntry,
            int methodKey,
            boolean protect,
            VMObfProfile profile)
    {
        int physicalField = profile.layoutSlot(LAYOUT_STATE_KEY);
        layout[slot * RECORD_SIZE + physicalField] = protect
                ? stateKey ^ (blockEntry
                        ? profile.stateMix(methodKey, slot)
                        : profile.chainedStateMix(methodKey, previousStateKey, slot, blockIndex))
                : stateKey;
    }

    private static void setBlock(
            int[] blocks,
            int blockIndex,
            int field,
            int value,
            int methodKey,
            boolean protect,
            VMObfProfile profile)
    {
        blocks[blockIndex * BLOCK_SIZE + field] = protect
                ? value ^ profile.blockMix(methodKey, blockIndex, field)
                : value;
    }

    private static int nonZeroRandom()
    {
        int value;
        do
        {
            value = RandomUtils.randomInt();
        } while (value == 0);
        return value;
    }

    private record ConstantLayout(Object[] constants, Map<Integer, Integer> indexByOriginal)
    {
    }

    private record OpcodeLayout(Map<Integer, Integer> virtualByRealOpcode, int[] encodedOpcodeMap)
    {
    }

    private record BasicBlock(int originalStartPc, int virtualStartPc, List<VMInstruction> instructions)
    {
    }

    private record ConstantBinding(int primary, int secondary)
    {
    }

    public record EncodedConstant(int[][] variants)
    {
    }
}
