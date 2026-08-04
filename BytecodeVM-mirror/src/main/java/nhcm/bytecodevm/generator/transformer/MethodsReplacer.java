package nhcm.bytecodevm.generator.transformer;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.utils.RandomUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class MethodsReplacer
{
    private final List<CompiledMethod> compiledMethods;
    private final String vmClassName;
    private final VMIntegrityPlan integrityPlan;
    private final Set<MethodNode> integrityProtectedMethods;

    public MethodsReplacer(List<CompiledMethod> compiledMethods, String vmClassName)
    {
        this(compiledMethods, vmClassName, null, null);
    }

    public MethodsReplacer(List<CompiledMethod> compiledMethods, String vmClassName, VMIntegrityPlan integrityPlan)
    {
        this(compiledMethods, vmClassName, integrityPlan, null);
    }

    public MethodsReplacer(
            List<CompiledMethod> compiledMethods,
            String vmClassName,
            VMIntegrityPlan integrityPlan,
            Set<MethodNode> integrityProtectedMethods)
    {
        this.compiledMethods = List.copyOf(Objects.requireNonNull(compiledMethods, "compiledMethods"));
        this.vmClassName = Objects.requireNonNull(vmClassName, "vmClassName");
        this.integrityPlan = integrityPlan;
        this.integrityProtectedMethods = copyIdentitySet(integrityProtectedMethods);
    }

    public Map<String, ClassNode> transform()
    {
        return transform(ignored -> { });
    }

    public Map<String, ClassNode> transform(Consumer<CompiledMethod> replacedMethod)
    {
        Objects.requireNonNull(replacedMethod, "replacedMethod");
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        for (CompiledMethod compiledMethod : compiledMethods)
        {
            validate(compiledMethod);
            classes.put(compiledMethod.owner.name, compiledMethod.owner);
            replace(compiledMethod);
            replacedMethod.accept(compiledMethod);
        }
        return classes;
    }

    private void replace(CompiledMethod compiledMethod)
    {
        ClassNode owner = compiledMethod.owner;
        MethodNode method = compiledMethod.source;
        boolean isStatic = compiledMethod.isStatic;
        int sourceLocal = isStatic ? 0 : 1;
        Type[] parameters = Type.getArgumentTypes(compiledMethod.descriptor);

        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        method.maxStack = 0;
        method.maxLocals = sourceLocal;

        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Type returnType = Type.getReturnType(method.desc);
        Local argArray = ib.var("args", "[Ljava/lang/Object;");
        int parameterSlots = 0;
        for (Type parameter : parameters)
        {
            parameterSlots += parameter.getSize();
        }
        ib.set(argArray, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(parameterSlots)));
        int parameterLocal = sourceLocal;
        for(int i = 0; i < parameters.length; i++)
        {
            Local value = ib.getLocal("DOES_NOT_MATTER" + i, parameters[i], parameterLocal);
            ib.setArray(argArray, AdvInsnBuilder.constant(parameterLocal - sourceLocal), value);
            parameterLocal += parameters[i].getSize();
        }
        Expr receiver = isStatic
                ? AdvInsnBuilder.constant(null)
                : AdvInsnBuilder.cast(AdvInsnBuilder.self(owner.name), "java/lang/Object");
        Local integrityKey = null;
        if (usesIntegrityCheck())
        {
            integrityKey = ib.var("integrityKey", "I");
            if (usesIntegrityCheck(compiledMethod))
            {
                ib.set(integrityKey, AdvInsnBuilder.callStatic(
                        integrityPlan.owner(),
                        integrityPlan.methodName(),
                        "I"));
            }
            else
            {
                ib.set(integrityKey, AdvInsnBuilder.constant(integrityPlan.expectedCapability()));
            }
        }
        Expr execute;
        if (compiledMethod.isSegmented())
        {
            Local codeIds = ib.var("codeIds", "[I");
            ib.set(codeIds, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(compiledMethod.codeIds.size())));
            for (int index = 0; index < compiledMethod.codeIds.size(); index++)
            {
                ib.setArray(codeIds, AdvInsnBuilder.constant(index), AdvInsnBuilder.constant(compiledMethod.codeIds.get(index)));
            }
            execute = integrityKey == null
                    ? AdvInsnBuilder.callStatic(
                            vmClassName,
                            "execute",
                            "Ljava/lang/Object;",
                            codeIds,
                            receiver,
                            argArray)
                    : AdvInsnBuilder.callStatic(
                            vmClassName,
                            "execute",
                            "Ljava/lang/Object;",
                            codeIds,
                            receiver,
                            argArray,
                            integrityKey);
        }
        else
        {
            execute = integrityKey == null
                    ? AdvInsnBuilder.callStatic(
                            vmClassName,
                            "execute",
                            "Ljava/lang/Object;",
                            AdvInsnBuilder.constant(compiledMethod.codeId),
                            receiver,
                            argArray)
                    : AdvInsnBuilder.callStatic(
                            vmClassName,
                            "execute",
                            "Ljava/lang/Object;",
                            AdvInsnBuilder.constant(compiledMethod.codeId),
                            receiver,
                            argArray,
                            integrityKey);
        }
        if(returnType.equals(Type.VOID_TYPE))
        {
            ib.directCall(execute);
            ib.returnVoid();
        } else
        {
            ib.returnValue(AdvInsnBuilder.cast(execute, returnType));
        }
    }

    private boolean usesIntegrityCheck()
    {
        return integrityPlan != null && integrityPlan.ratio() > 0.0D;
    }

    private boolean usesIntegrityCheck(CompiledMethod method)
    {
        if (!usesIntegrityCheck())
        {
            return false;
        }
        return integrityProtectedMethods == null
                ? RandomUtils.randomDouble() <= integrityPlan.ratio()
                : integrityProtectedMethods.contains(method.source);
    }

    private static Set<MethodNode> copyIdentitySet(Set<MethodNode> source)
    {
        if (source == null)
        {
            return null;
        }
        Set<MethodNode> copy = Collections.newSetFromMap(new IdentityHashMap<>());
        copy.addAll(source);
        return Collections.unmodifiableSet(copy);
    }

    private static void validate(CompiledMethod compiledMethod)
    {
        Objects.requireNonNull(compiledMethod, "compiledMethod");
        Objects.requireNonNull(compiledMethod.owner, "compiledMethod.owner");
        MethodNode method = Objects.requireNonNull(compiledMethod.source, "compiledMethod.source");

        if (!compiledMethod.owner.methods.contains(method))
        {
            throw new IllegalArgumentException("Method does not belong to owner: " + method.name + method.desc);
        }
        if ("<init>".equals(method.name))
        {
            throw new IllegalArgumentException("Initializers cannot be replaced: " + method.name + method.desc);
        }
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
        {
            throw new IllegalArgumentException("Method has no replaceable bytecode: " + method.name + method.desc);
        }
        if (!method.desc.equals(compiledMethod.descriptor))
        {
            throw new IllegalArgumentException("Compiled method descriptor no longer matches " + method.name);
        }
        if (MethodUtils.isStatic(method) != compiledMethod.isStatic)
        {
            throw new IllegalArgumentException("Compiled method static flag no longer matches " + method.name + method.desc);
        }
    }
}
