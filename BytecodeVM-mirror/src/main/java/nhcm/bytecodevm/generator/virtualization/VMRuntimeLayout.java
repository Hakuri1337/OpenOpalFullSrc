package nhcm.bytecodevm.generator.virtualization;

import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.utils.builder.FieldRef;
import nhcm.bytecodevm.utils.builder.MethodRef;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;

public class VMRuntimeLayout
{
    public final String owner;
    private final GeneratedMemberNamer namer;

    public final FieldRef codePools;
    public final FieldRef fieldHandles;
    public final FieldRef methodHandles;
    public final FieldRef methodTypes;
    public final FieldRef monitors;

    public final MethodRef constantString;
    public final MethodRef resolve;
    public final MethodRef interpret;
    public final MethodRef interpretStep;
    public final MethodRef registerRead;
    public final MethodRef registerWrite;
    public final MethodRef executeRegisterOp;
    public final MethodRef executeDataFlow;
    public final MethodRef instructionIndex;
    public final MethodRef instructionIndexInBlock;
    public final MethodRef decodeOpcode;
    public final MethodRef decodeNextPc;
    public final MethodRef decodeOriginalPc;
    public final MethodRef decodeOperand;
    public final MethodRef layoutValue;
    public final MethodRef blockValue;
    public final MethodRef stateKey;
    public final MethodRef syncState;
    public final MethodRef mix;
    public final MethodRef dispatchKey;
    public final MethodRef methodType;
    public final MethodRef resolveConstant;
    public final MethodRef loadOwner;
    public final MethodRef loadOwnerWithLoader;
    public final MethodRef invoke;
    public final MethodRef construct;
    public final MethodRef getField;
    public final MethodRef setField;
    public final MethodRef fieldHandle;
    public final MethodRef adaptFieldHandle;
    public final MethodRef unsafe;
    public final MethodRef unsafeSetStaticField;
    public final MethodRef findField;
    public final MethodRef findMethod;
    public final MethodRef adaptMethodHandle;
    public final MethodRef adaptDirectMethodHandle;
    public final MethodRef adaptConstructorHandle;
    public final MethodRef coerceArgument;
    public final MethodRef cloneArray;
    public final MethodRef findExceptionHandler;
    public final MethodRef monitorFor;
    public final MethodRef monitorEnter;
    public final MethodRef monitorExit;
    public final MethodRef rethrow;

    public VMRuntimeLayout(String owner, String frameDescriptor)
    {
        this(owner, frameDescriptor, null, GeneratedMemberNamer.DISABLED);
    }

    public VMRuntimeLayout(String owner, String frameDescriptor, String programDescriptor)
    {
        this(owner, frameDescriptor, programDescriptor, GeneratedMemberNamer.DISABLED);
    }

    public VMRuntimeLayout(String owner, String frameDescriptor, String programDescriptor, GeneratedMemberNamer namer)
    {
        this(owner, frameDescriptor, programDescriptor, namer, null);
    }

    public VMRuntimeLayout(
            String owner,
            String frameDescriptor,
            String programDescriptor,
            GeneratedMemberNamer namer,
            VMStructure structure)
    {
        this.owner = owner;
        this.namer = namer;

        this.codePools = field("CODE_POOLS", "Ljava/util/List;");
        this.fieldHandles = field("FIELD_HANDLES", "Ljava/util/Map;");
        this.methodHandles = field("METHOD_HANDLES", "Ljava/util/Map;");
        this.methodTypes = field("METHOD_TYPES", "Ljava/util/Map;");
        this.monitors = field("MONITORS", "Ljava/util/Map;");

        this.constantString = method(
                "constantString",
                programDescriptor == null
                        ? "([Ljava/lang/Object;I)Ljava/lang/String;"
                        : "(" + programDescriptor + frameDescriptor + "[Ljava/lang/Object;III)Ljava/lang/String;");
        this.resolve = programDescriptor == null ? null : method("resolve", "(I)" + programDescriptor);
        this.interpret = programDescriptor == null ? null : method(
                interpreterName(structure),
                "(" + programDescriptor + frameDescriptor + ")V");
        this.interpretStep = programDescriptor == null ? null : method(
                kernelName(structure),
                kernelDescriptor(programDescriptor, frameDescriptor, structure));
        this.registerRead = programDescriptor == null ? null : method(
                "registerRead",
                "(" + programDescriptor + frameDescriptor + "II)Ljava/lang/Object;");
        this.registerWrite = programDescriptor == null ? null : method(
                "registerWrite",
                "(" + programDescriptor + frameDescriptor + "IILjava/lang/Object;I)V");
        this.executeRegisterOp = programDescriptor == null ? null : method(
                "executeRegisterOp",
                "(" + programDescriptor + frameDescriptor + "[Ljava/lang/Object;IIIIIIIII)V");
        this.executeDataFlow = programDescriptor == null ? null : method(
                "executeDataFlow",
                "(" + programDescriptor + frameDescriptor + "[Ljava/lang/Object;[III)V");
        this.instructionIndex = programDescriptor == null ? null : method("instructionIndex", "(" + programDescriptor + frameDescriptor + "I)I");
        this.instructionIndexInBlock = programDescriptor == null ? null : method("instructionIndexInBlock", "(" + programDescriptor + "II)I");
        this.decodeOpcode = programDescriptor == null ? null : method("decodeOpcode", "(" + programDescriptor + frameDescriptor + "I)I");
        this.decodeNextPc = programDescriptor == null ? null : method("decodeNextPc", "(" + programDescriptor + frameDescriptor + "I)I");
        this.decodeOriginalPc = programDescriptor == null ? null : method("decodeOriginalPc", "(" + programDescriptor + frameDescriptor + "I)I");
        this.decodeOperand = programDescriptor == null ? null : method("decodeOperand", "(" + programDescriptor + frameDescriptor + "III)I");
        this.layoutValue = programDescriptor == null ? null : method("layoutValue", "(" + programDescriptor + "III)I");
        this.blockValue = programDescriptor == null ? null : method("blockValue", "(" + programDescriptor + "II)I");
        this.stateKey = programDescriptor == null ? null : method("stateKey", "(" + programDescriptor + "I)I");
        this.syncState = programDescriptor == null ? null : method("syncState", "(" + programDescriptor + frameDescriptor + "I)V");
        this.mix = method("mix", "(IIII)I");
        this.dispatchKey = method("dispatchKey", "(I)I");
        this.methodType = method("methodType", "(Ljava/lang/String;)Ljava/lang/invoke/MethodType;");
        this.resolveConstant = method(
                "resolveConstant",
                programDescriptor == null
                        ? "(Ljava/lang/Object;" + frameDescriptor + ")Ljava/lang/Object;"
                        : "(" + programDescriptor + "Ljava/lang/Object;" + frameDescriptor + "II)Ljava/lang/Object;");
        this.loadOwner = method("loadOwner", "(Ljava/lang/String;)Ljava/lang/Class;");
        this.loadOwnerWithLoader = method("loadOwner", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Class;");
        this.invoke = method(
                "invoke",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                        "ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.construct = method(
                "construct",
                "(Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.getField = method(
                "getField",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;)Ljava/lang/Object;");
        this.setField = method(
                "setField",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;Ljava/lang/Object;)V");
        this.fieldHandle = method(
                "fieldHandle",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/invoke/MethodHandle;");
        this.adaptFieldHandle = method(
                "adaptFieldHandle",
                "(Ljava/lang/reflect/Field;ZZ)Ljava/lang/invoke/MethodHandle;");
        this.unsafe = method(
                "unsafe",
                "()Lsun/misc/Unsafe;");
        this.unsafeSetStaticField = method(
                "unsafeSetStaticField",
                "(Ljava/lang/reflect/Field;Ljava/lang/Object;)V");
        this.findField = method(
                "findField",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;");
        this.findMethod = method(
                "findMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        this.adaptMethodHandle = method(
                "adaptMethodHandle",
                "(Ljava/lang/reflect/Method;ZI)Ljava/lang/invoke/MethodHandle;");
        this.adaptDirectMethodHandle = method(
                "adaptDirectMethodHandle",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Z)Ljava/lang/invoke/MethodHandle;");
        this.adaptConstructorHandle = method(
                "adaptConstructorHandle",
                "(Ljava/lang/reflect/Constructor;I)Ljava/lang/invoke/MethodHandle;");
        this.coerceArgument = method(
                "coerceArgument",
                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
        this.cloneArray = method("cloneArray", "(Ljava/lang/Object;)Ljava/lang/Object;");
        this.findExceptionHandler = method(
                "findExceptionHandler",
                programDescriptor == null
                        ? "(Ljava/lang/Throwable;[III[Ljava/lang/Object;)I"
                        : "(Ljava/lang/Throwable;[IIII" + programDescriptor + frameDescriptor + "[Ljava/lang/Object;)I");
        this.monitorFor = method(
                "monitorFor",
                "(Ljava/lang/Object;)Ljava/util/concurrent/locks/ReentrantLock;");
        this.monitorEnter = method("monitorEnter", "(Ljava/lang/Object;)V");
        this.monitorExit = method("monitorExit", "(Ljava/lang/Object;)V");
        this.rethrow = method("rethrow", "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
    }

    private FieldRef field(String name, String descriptor)
    {
        return new FieldRef(owner, namer.field(owner, name), descriptor);
    }

    private MethodRef method(String name, String descriptor)
    {
        return new MethodRef(owner, namer.method(owner, name, descriptor), descriptor);
    }

    private static String interpreterName(VMStructure structure)
    {
        if (structure == null)
        {
            return "interpret";
        }
        return switch (structure)
        {
            case SIMPLE_DISPATCH -> "interpret";
            case DISTRIBUTED_DISPATCH -> "runDistributedDispatch";
            case MULTIPLE_DISPATCH -> "runMultipleDispatch";
            case THREADED_DIRECT -> "runDirectThread";
            case THREADED_INDIRECT -> "runIndirectThread";
            case CALL_THREADED -> "runCallThread";
            case RECURSIVE -> "runRecursiveMachine";
            case CONTINUATION_PASSING -> "runContinuations";
            case OBJECT -> "runInstructionObjects";
            case POLYMORPHIC -> "runPolymorphicMachine";
            case SELF_MODIFYING -> "runMutableMachine";
            case REGISTER_BASED -> "runRegisterMachine";
            case DATA_FLOW -> "runDataFlowGraph";
            case GRAPH -> "walkExecutionGraph";
            case FSM -> "runFiniteStateMachine";
            case EVENT -> "pumpVirtualEvents";
            case COROUTINE -> "runCoroutineMachine";
            case LOW, MEDIUM, HIGH -> throw new IllegalArgumentException("Automatic structure is unresolved");
        };
    }

    private static String kernelName(VMStructure structure)
    {
        if (structure == null)
        {
            return "interpretStep";
        }
        return switch (structure)
        {
            case SIMPLE_DISPATCH -> "interpretStep";
            case DISTRIBUTED_DISPATCH -> "routeDistributedShard";
            case MULTIPLE_DISPATCH -> "selectDispatcherVariant";
            case THREADED_DIRECT -> "advanceDirectHandler";
            case THREADED_INDIRECT -> "resolveIndirectHandler";
            case CALL_THREADED -> "invokeCallThreadHandler";
            case RECURSIVE -> "recurseInstruction";
            case CONTINUATION_PASSING -> "applyContinuation";
            case OBJECT -> "executeInstructionObject";
            case POLYMORPHIC -> "executeHandlerVariant";
            case SELF_MODIFYING -> "decodeMutableInstruction";
            case REGISTER_BASED -> "executeRegisterInstruction";
            case DATA_FLOW -> "scheduleReadyNode";
            case GRAPH -> "visitExecutionNode";
            case FSM -> "transitionState";
            case EVENT -> "deliverVirtualEvent";
            case COROUTINE -> "resumeVirtualCoroutine";
            case LOW, MEDIUM, HIGH -> throw new IllegalArgumentException("Automatic structure is unresolved");
        };
    }

    private static String kernelDescriptor(
            String programDescriptor,
            String frameDescriptor,
            VMStructure structure)
    {
        StringBuilder descriptor = new StringBuilder("(")
                .append(programDescriptor)
                .append(frameDescriptor)
                .append("[I[Ljava/lang/Object;I");
        if (structure != null && structure != VMStructure.SIMPLE_DISPATCH)
        {
            int shape = structure.ordinal();
            for (int bit = 0; bit < 5; bit++)
            {
                descriptor.append((shape & 1 << bit) == 0 ? 'I' : 'J');
            }
        }
        return descriptor.append(")I").toString();
    }
}
