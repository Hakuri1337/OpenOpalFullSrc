package nhcm.bytecodevm.generator.transformer;

import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.builder.InsnBuilder;
import nhcm.bytecodevm.utils.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class InvocationBridgeGenerator
{
    private final Map<ClassNode, Integer> nextIds = new IdentityHashMap<>();
    private final GeneratedMemberNamer namer;

    public InvocationBridgeGenerator()
    {
        this(GeneratedMemberNamer.DISABLED);
    }

    public InvocationBridgeGenerator(GeneratedMemberNamer namer)
    {
        this.namer = namer;
    }

    public List<MethodNode> rewrite(ClassNode owner, MethodNode method)
    {
        List<MethodNode> bridges = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray())
        {
            if (instruction instanceof InvokeDynamicInsnNode dynamicInsn)
            {
                bridges.add(rewriteInvokeDynamic(owner, method, dynamicInsn));
            }
            else if (instruction instanceof MethodInsnNode methodInsn &&
                    methodInsn.getOpcode() == Opcodes.INVOKESPECIAL &&
                    !"<init>".equals(methodInsn.name))
            {
                bridges.add(rewriteInvokeSpecial(owner, method, methodInsn));
            }
        }
        return bridges;
    }

    private MethodNode rewriteInvokeDynamic(
            ClassNode owner,
            MethodNode source,
            InvokeDynamicInsnNode invocation)
    {
        String bridgeName = nextBridgeName(owner, invocation.desc);
        MethodNode bridge = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                bridgeName,
                invocation.desc,
                null,
                null);
        InsnBuilder ib = new InsnBuilder(bridge.instructions);
        if (isStringConcat(invocation))
        {
            emitStringConcat(ib, invocation);
        }
        else
        {
            loadArguments(ib, Type.getArgumentTypes(invocation.desc), 0);
            ib.invokeDynamic(
                    invocation.name,
                    invocation.desc,
                    invocation.bsm,
                    invocation.bsmArgs.clone());
        }
        TypeUtils.returnValue(ib, Type.getReturnType(invocation.desc));
        setBridgeLimits(bridge);
        owner.methods.add(bridge);

        source.instructions.set(invocation, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner.name,
                bridgeName,
                invocation.desc,
                false));
        return bridge;
    }

    private MethodNode rewriteInvokeSpecial(
            ClassNode owner,
            MethodNode source,
            MethodInsnNode invocation)
    {
        Type[] arguments = Type.getArgumentTypes(invocation.desc);
        Type[] bridgeArguments = new Type[arguments.length + 1];
        bridgeArguments[0] = Type.getObjectType(owner.name);
        System.arraycopy(arguments, 0, bridgeArguments, 1, arguments.length);
        String bridgeDescriptor = Type.getMethodDescriptor(
                Type.getReturnType(invocation.desc),
                bridgeArguments);
        String bridgeName = nextBridgeName(owner, bridgeDescriptor);

        MethodNode bridge = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                bridgeName,
                bridgeDescriptor,
                null,
                null);
        InsnBuilder ib = new InsnBuilder(bridge.instructions);
        ib.aload(0);
        loadArguments(ib, arguments, 1);
        ib.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                invocation.owner,
                invocation.name,
                invocation.desc,
                invocation.itf));
        TypeUtils.returnValue(ib, Type.getReturnType(invocation.desc));
        setBridgeLimits(bridge);
        owner.methods.add(bridge);

        source.instructions.set(invocation, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner.name,
                bridgeName,
                bridgeDescriptor,
                false));
        return bridge;
    }

    public static boolean canVirtualizeBridge(MethodNode method)
    {
        for (AbstractInsnNode instruction : method.instructions)
        {
            if (instruction instanceof InvokeDynamicInsnNode)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isStringConcat(InvokeDynamicInsnNode invocation)
    {
        return "java/lang/invoke/StringConcatFactory".equals(invocation.bsm.getOwner()) &&
               ("makeConcat".equals(invocation.bsm.getName()) ||
                "makeConcatWithConstants".equals(invocation.bsm.getName())) &&
               Type.getReturnType(invocation.desc).equals(Type.getType(String.class));
    }

    private static void emitStringConcat(InsnBuilder ib, InvokeDynamicInsnNode invocation)
    {
        Type[] arguments = Type.getArgumentTypes(invocation.desc);
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "()V");

        if ("makeConcatWithConstants".equals(invocation.bsm.getName()) &&
            invocation.bsmArgs.length > 0 &&
            invocation.bsmArgs[0] instanceof String recipe)
        {
            emitConcatRecipe(ib, arguments, recipe, invocation.bsmArgs);
        }
        else
        {
            int local = 0;
            for (Type argument : arguments)
            {
                TypeUtils.load(ib, argument, local);
                appendValue(ib, argument);
                local += argument.getSize();
            }
        }

        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
    }

    private static void emitConcatRecipe(InsnBuilder ib, Type[] arguments, String recipe, Object[] bootstrapArguments)
    {
        int argumentIndex = 0;
        int argumentLocal = 0;
        int constantIndex = 1;
        StringBuilder literal = new StringBuilder();

        for (int index = 0; index < recipe.length(); index++)
        {
            char value = recipe.charAt(index);
            if (value == '\u0001' || value == '\u0002')
            {
                appendLiteral(ib, literal);
                if (value == '\u0001')
                {
                    Type argument = arguments[argumentIndex++];
                    TypeUtils.load(ib, argument, argumentLocal);
                    appendValue(ib, argument);
                    argumentLocal += argument.getSize();
                }
                else
                {
                    Object constant = constantIndex < bootstrapArguments.length
                            ? bootstrapArguments[constantIndex++]
                            : "";
                    ib.ldc(String.valueOf(constant));
                    appendValue(ib, Type.getType(String.class));
                }
                continue;
            }
            literal.append(value);
        }
        appendLiteral(ib, literal);
    }

    private static void appendLiteral(InsnBuilder ib, StringBuilder literal)
    {
        if (literal.isEmpty())
        {
            return;
        }
        ib.ldc(literal.toString());
        appendValue(ib, Type.getType(String.class));
        literal.setLength(0);
    }

    private static void appendValue(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;");
            case Type.CHAR -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;");
            case Type.BYTE, Type.SHORT, Type.INT -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
            case Type.LONG -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;");
            case Type.FLOAT -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(F)Ljava/lang/StringBuilder;");
            case Type.DOUBLE -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(D)Ljava/lang/StringBuilder;");
            case Type.OBJECT -> {
                if (type.equals(Type.getType(String.class)))
                {
                    ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
                }
                else
                {
                    ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
                }
            }
            case Type.ARRAY -> ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
            default -> throw new IllegalArgumentException("Unsupported concat argument: " + type);
        }
    }

    private static void setBridgeLimits(MethodNode bridge)
    {
        int locals = 0;
        for (Type argument : Type.getArgumentTypes(bridge.desc))
        {
            locals += argument.getSize();
        }
        bridge.maxLocals = locals;
        bridge.maxStack = Math.max(16, locals + 4);
    }

    private String nextBridgeName(ClassNode owner, String descriptor)
    {
        int id = nextIds.getOrDefault(owner, 0);
        String name;
        do
        {
            name = namer.method(owner.name, "$vm$invoke$" + id++, descriptor);
        }
        while (hasMethodNamed(owner, name));
        nextIds.put(owner, id);
        return name;
    }

    private static boolean hasMethodNamed(ClassNode owner, String name)
    {
        for (MethodNode method : owner.methods)
        {
            if (method.name.equals(name))
            {
                return true;
            }
        }
        return false;
    }

    private static void loadArguments(InsnBuilder ib, Type[] arguments, int local)
    {
        for (Type argument : arguments)
        {
            TypeUtils.load(ib, argument, local);
            local += argument.getSize();
        }
    }
}
