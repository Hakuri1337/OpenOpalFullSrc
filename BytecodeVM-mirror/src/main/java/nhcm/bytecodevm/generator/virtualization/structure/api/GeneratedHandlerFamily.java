package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import nhcm.bytecodevm.utils.builder.FieldRef;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;

/** Generates compact handler shards with randomized decision trees instead of opcode switches. */
public final class GeneratedHandlerFamily
{
    private static final int SHARD_SIZE = 32;

    private final String interfaceName;
    private final String methodName;
    private final String descriptor;
    private final int structureShape;
    private final Map<Long, HandlerReference> implementationByPrimaryKey;

    private GeneratedHandlerFamily(
            String interfaceName,
            String methodName,
            String descriptor,
            int structureShape,
            Map<Long, HandlerReference> implementationByPrimaryKey)
    {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.structureShape = structureShape;
        this.implementationByPrimaryKey = Map.copyOf(implementationByPrimaryKey);
    }

    public static GeneratedHandlerFamily create(
            VMDispatchGenerationContext dispatch,
            String familyName,
            int variantsPerOpcode)
    {
        VMStructureGenerationContext generation = dispatch.generation();
        int structureShape = generation.plan().structure().ordinal();
        String interfaceName = generation.className(familyName + "$Handler");
        String descriptor = handlerDescriptor(generation, structureShape);
        String methodName = generation.methodName(familyName + "$execute", descriptor);

        ClassNode handlerInterface = ClassUtils.newClassNode(
                new Acc[]{Acc.PUBLIC, Acc.INTERFACE, Acc.ABSTRACT},
                interfaceName);
        handlerInterface.methods.add(MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.ABSTRACT},
                methodName,
                descriptor));
        generation.addAuxiliaryClass(handlerInterface);

        Map<Integer, VMDispatchTarget> primaryTargets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            primaryTargets.putIfAbsent(target.primaryKey(), target);
        }
        Map<Long, HandlerReference> implementations = new LinkedHashMap<>();
        List<HandlerSpec> specs = new ArrayList<>();
        Set<Integer> tokens = new HashSet<>();
        for (VMDispatchTarget target : primaryTargets.values())
        {
            for (int variant = 0; variant < Math.max(1, variantsPerOpcode); variant++)
            {
                int token;
                do
                {
                    token = RandomUtils.randomInt();
                } while (!tokens.add(token));
                HandlerReference reference = new HandlerReference(token);
                specs.add(new HandlerSpec(token, target, variant, reference));
                implementations.put(compoundKey(target.primaryKey(), variant), reference);
            }
        }

        for (int from = 0; from < specs.size(); from += SHARD_SIZE)
        {
            int to = Math.min(specs.size(), from + SHARD_SIZE);
            String shardName = generation.className(familyName + "$Shard$" + from / SHARD_SIZE);
            List<HandlerSpec> shard = new ArrayList<>(specs.subList(from, to));
            shard.sort(Comparator.comparingInt(spec -> spec.token));
            for (HandlerSpec spec : shard)
            {
                spec.reference.implementationName = shardName;
            }
            generation.addAuxiliaryClass(createShard(
                    generation,
                    interfaceName,
                    methodName,
                    descriptor,
                    shardName,
                    shard));
        }

        return new GeneratedHandlerFamily(
                interfaceName,
                methodName,
                descriptor,
                structureShape,
                implementations);
    }

    private static ClassNode createShard(
            VMStructureGenerationContext generation,
            String interfaceName,
            String methodName,
            String descriptor,
            String implementationName,
            List<HandlerSpec> specs)
    {
        ClassNode implementation = ClassUtils.newClassNode(
                new Acc[]{Acc.PUBLIC, Acc.FINAL},
                implementationName);
        implementation.interfaces.add(interfaceName);
        FieldRef tokenField = new FieldRef(
                implementationName,
                generation.fieldName(implementationName, "semanticToken"),
                "I");
        implementation.fields.add(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.FINAL},
                tokenField.name(),
                tokenField.descriptor()));

        MethodNode init = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "<init>", "(I)V");
        AdvInsnBuilder initBuilder = new AdvInsnBuilder(init);
        Local initToken = initBuilder.getLocal("semanticToken", "I", 1);
        initBuilder.callNoArgSuperConstructor("java/lang/Object");
        initBuilder.set(
                AdvInsnBuilder.field(AdvInsnBuilder.self(implementationName), tokenField),
                initToken);
        initBuilder.returnVoid();
        implementation.methods.add(init);

        MethodNode execute = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.FINAL},
                methodName,
                descriptor);
        AdvInsnBuilder ib = new AdvInsnBuilder(execute);
        Local token = ib.var("activeSemanticToken", "I");
        ib.set(token, AdvInsnBuilder.field(AdvInsnBuilder.self(implementationName), tokenField));
        emitDecisionTree(generation, ib, token, specs, 0, specs.size());
        implementation.methods.add(execute);
        return implementation;
    }

    private static void emitDecisionTree(
            VMStructureGenerationContext generation,
            AdvInsnBuilder ib,
            Local token,
            List<HandlerSpec> specs,
            int from,
            int to)
    {
        if (from >= to)
        {
            ib.returnValue(AdvInsnBuilder.constant(0));
            return;
        }
        int middle = (from + to) >>> 1;
        HandlerSpec spec = specs.get(middle);
        ib.ifElse(
                AdvInsnBuilder.equal(token, AdvInsnBuilder.constant(spec.token)),
                match -> {
                    emitTargetCall(generation, match, spec);
                    match.returnValue(AdvInsnBuilder.constant(1));
                },
                mismatch -> mismatch.ifElse(
                        AdvInsnBuilder.lessThan(token, AdvInsnBuilder.constant(spec.token)),
                        lower -> emitDecisionTree(generation, lower, token, specs, from, middle),
                        higher -> emitDecisionTree(generation, higher, token, specs, middle + 1, to)));
    }

    private static void emitTargetCall(
            VMStructureGenerationContext generation,
            AdvInsnBuilder ib,
            HandlerSpec spec)
    {
        int shape = generation.plan().structure().ordinal();
        Map<CoreArgument, Integer> slots = coreSlots(shape);
        Local program = ib.getLocal(
                "program",
                generation.programLayout().owner,
                slots.get(CoreArgument.PROGRAM));
        Local frame = ib.getLocal(
                "frame",
                generation.frameLayout().owner,
                slots.get(CoreArgument.FRAME));
        Local code = ib.getLocal("code", "[I", slots.get(CoreArgument.CODE));
        Local constants = ib.getLocal(
                "constants",
                "[Ljava/lang/Object;",
                slots.get(CoreArgument.CONSTANTS));
        Local opcode = ib.getLocal("opcode", "I", slots.get(CoreArgument.OPCODE));
        Local instructionIndex = ib.getLocal(
                "instructionIndex",
                "I",
                slots.get(CoreArgument.INSTRUCTION_INDEX));
        if ((spec.variant & 1) != 0)
        {
            Local guard = ib.var("semanticGuard", "I");
            ib.set(guard, AdvInsnBuilder.bitXor(
                    opcode,
                    AdvInsnBuilder.constant(spec.target.primaryKey())));
            ib.ifCondition(
                    AdvInsnBuilder.equal(guard, AdvInsnBuilder.constant(Integer.MIN_VALUE)),
                    impossible -> impossible.returnValue(AdvInsnBuilder.constant(0)));
        }
        Map<SemanticArgument, Expr> semantic = new EnumMap<>(SemanticArgument.class);
        semantic.put(SemanticArgument.PROGRAM, program);
        semantic.put(SemanticArgument.FRAME, frame);
        semantic.put(SemanticArgument.CODE, code);
        semantic.put(SemanticArgument.CONSTANTS, constants);
        semantic.put(SemanticArgument.OPCODE, opcode);
        semantic.put(
                SemanticArgument.OPCODE_INDEX,
                AdvInsnBuilder.constant(spec.target.handlerIndex()));
        semantic.put(SemanticArgument.INSTRUCTION_INDEX, instructionIndex);
        List<Expr> arguments = new ArrayList<>();
        for (SemanticArgument argument : semanticCoreOrder(shape))
        {
            arguments.add(semantic.get(argument));
        }
        for (int bit = 0; bit < 5; bit++)
        {
            arguments.add((shape & 1 << bit) == 0
                    ? AdvInsnBuilder.constant(generation.profile().decodeVariant ^ bit)
                    : AdvInsnBuilder.constant(
                            ((long) generation.profile().decodeVariant << 32) ^
                            (0xD1B54A32D192ED03L + bit)));
        }
        ib.directCall(AdvInsnBuilder.callStatic(
                generation.owner(),
                spec.target.handlerName(),
                "V",
                arguments.toArray(Expr[]::new)));
    }

    public String interfaceName()
    {
        return interfaceName;
    }

    public String descriptor()
    {
        return descriptor;
    }

    public String methodName()
    {
        return methodName;
    }

    public Expr newHandler(int primaryKey)
    {
        return newHandler(primaryKey, 0);
    }

    public Expr newHandler(int primaryKey, int variant)
    {
        HandlerReference reference = implementationByPrimaryKey.get(compoundKey(primaryKey, variant));
        if (reference == null || reference.implementationName == null)
        {
            throw new IllegalArgumentException("No generated handler for key " + primaryKey + ", variant " + variant);
        }
        return AdvInsnBuilder.newObject(
                reference.implementationName,
                AdvInsnBuilder.constant(reference.token));
    }

    public Expr invoke(Expr handler, InterpretContext runtime)
    {
        Map<CoreArgument, Expr> core = new EnumMap<>(CoreArgument.class);
        core.put(CoreArgument.PROGRAM, runtime.program());
        core.put(CoreArgument.FRAME, runtime.frame());
        core.put(CoreArgument.CODE, runtime.code());
        core.put(CoreArgument.CONSTANTS, runtime.constants());
        core.put(CoreArgument.OPCODE, runtime.opcode());
        core.put(CoreArgument.INSTRUCTION_INDEX, runtime.instructionIndex());
        List<Expr> arguments = new ArrayList<>();
        for (CoreArgument argument : coreOrder(structureShape))
        {
            arguments.add(core.get(argument));
        }
        for (int bit = 0; bit < 5; bit++)
        {
            if ((structureShape & 1 << bit) == 0)
            {
                arguments.add(AdvInsnBuilder.bitXor(
                        runtime.structureState(),
                        AdvInsnBuilder.constant(bit)));
            }
            else
            {
                arguments.add(AdvInsnBuilder.constant(
                        ((long) structureShape << 32) ^ (0x9E3779B97F4A7C15L + bit)));
            }
        }
        return AdvInsnBuilder.callInterface(
                handler,
                interfaceName,
                methodName,
                "I",
                arguments.toArray(Expr[]::new));
    }

    private static String handlerDescriptor(VMStructureGenerationContext generation, int structureShape)
    {
        StringBuilder descriptor = new StringBuilder("(");
        for (CoreArgument argument : coreOrder(structureShape))
        {
            descriptor.append(switch (argument)
            {
                case PROGRAM -> "L" + generation.programLayout().owner + ";";
                case FRAME -> "L" + generation.frameLayout().owner + ";";
                case CODE -> "[I";
                case CONSTANTS -> "[Ljava/lang/Object;";
                case OPCODE, INSTRUCTION_INDEX -> "I";
            });
        }
        for (int bit = 0; bit < 5; bit++)
        {
            descriptor.append((structureShape & 1 << bit) == 0 ? 'I' : 'J');
        }
        return descriptor.append(")I").toString();
    }

    private static Map<CoreArgument, Integer> coreSlots(int structureShape)
    {
        Map<CoreArgument, Integer> slots = new EnumMap<>(CoreArgument.class);
        int slot = 1;
        for (CoreArgument argument : coreOrder(structureShape))
        {
            slots.put(argument, slot++);
        }
        return slots;
    }

    private static List<CoreArgument> coreOrder(int structureShape)
    {
        List<CoreArgument> remaining = new ArrayList<>(List.of(CoreArgument.values()));
        List<CoreArgument> order = new ArrayList<>(remaining.size());
        int rank = structureShape;
        while (!remaining.isEmpty())
        {
            int selected = Math.floorMod(rank, remaining.size());
            rank /= remaining.size();
            order.add(remaining.remove(selected));
        }
        return order;
    }

    private static List<SemanticArgument> semanticCoreOrder(int structureShape)
    {
        List<SemanticArgument> remaining = new ArrayList<>(List.of(SemanticArgument.values()));
        List<SemanticArgument> order = new ArrayList<>(remaining.size());
        int rank = structureShape;
        while (!remaining.isEmpty())
        {
            int selected = Math.floorMod(rank, remaining.size());
            rank /= remaining.size();
            order.add(remaining.remove(selected));
        }
        return order;
    }

    private static long compoundKey(int primaryKey, int variant)
    {
        return ((long) primaryKey << 32) ^ (variant & 0xffffffffL);
    }

    private static final class HandlerReference
    {
        private final int token;
        private String implementationName;

        private HandlerReference(int token)
        {
            this.token = token;
        }
    }

    private record HandlerSpec(
            int token,
            VMDispatchTarget target,
            int variant,
            HandlerReference reference)
    {
    }

    private enum CoreArgument
    {
        PROGRAM,
        FRAME,
        CODE,
        CONSTANTS,
        OPCODE,
        INSTRUCTION_INDEX
    }

    private enum SemanticArgument
    {
        PROGRAM,
        FRAME,
        CODE,
        CONSTANTS,
        OPCODE,
        OPCODE_INDEX,
        INSTRUCTION_INDEX
    }
}
