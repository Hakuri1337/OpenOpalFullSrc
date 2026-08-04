package nhcm.bytecodevm.generator.globalclass;

import nhcm.bytecodevm.utils.builder.FieldRef;
import nhcm.bytecodevm.utils.builder.MethodRef;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;

public class VMProgramLayout
{
    public final String owner;
    private final GeneratedMemberNamer namer;

    public final FieldRef opcodeStreamField;
    public final FieldRef operandStreamField;
    public final FieldRef layoutStreamField;
    public final FieldRef blockStreamField;
    public final FieldRef constantsField;
    public final FieldRef exceptionHandlersField;
    public final FieldRef opcodeMapField;
    public final FieldRef methodKeyField;
    public final FieldRef featureFlagsField;
    public final FieldRef maxLocalsField;
    public final FieldRef maxStackField;

    public final MethodRef init;
    public final MethodRef opcodeStream;
    public final MethodRef operandStream;
    public final MethodRef layoutStream;
    public final MethodRef blockStream;
    public final MethodRef constants;
    public final MethodRef exceptionHandlers;
    public final MethodRef opcodeMap;
    public final MethodRef methodKey;
    public final MethodRef featureFlags;
    public final MethodRef maxLocals;
    public final MethodRef maxStack;

    public VMProgramLayout(String owner)
    {
        this(owner, GeneratedMemberNamer.DISABLED);
    }

    public VMProgramLayout(String owner, GeneratedMemberNamer namer)
    {
        this.owner = owner;
        this.namer = namer;

        this.opcodeStreamField = field("opcodeStream", "[I");
        this.operandStreamField = field("operandStream", "[I");
        this.layoutStreamField = field("layoutStream", "[I");
        this.blockStreamField = field("blockStream", "[I");
        this.constantsField = field("constants", "[Ljava/lang/Object;");
        this.exceptionHandlersField = field("exceptionHandlers", "[I");
        this.opcodeMapField = field("opcodeMap", "[I");
        this.methodKeyField = field("methodKey", "I");
        this.featureFlagsField = field("featureFlags", "I");
        this.maxLocalsField = field("maxLocals", "I");
        this.maxStackField = field("maxStack", "I");

        this.init = method("<init>", "([I[I[I[I[Ljava/lang/Object;[I[IIIII)V");
        this.opcodeStream = method("opcodeStream", "()[I");
        this.operandStream = method("operandStream", "()[I");
        this.layoutStream = method("layoutStream", "()[I");
        this.blockStream = method("blockStream", "()[I");
        this.constants = method("constants", "()[Ljava/lang/Object;");
        this.exceptionHandlers = method("exceptionHandlers", "()[I");
        this.opcodeMap = method("opcodeMap", "()[I");
        this.methodKey = method("methodKey", "()I");
        this.featureFlags = method("featureFlags", "()I");
        this.maxLocals = method("maxLocals", "()I");
        this.maxStack = method("maxStack", "()I");
    }

    private FieldRef field(String name, String descriptor)
    {
        return new FieldRef(owner, namer.field(owner, name), descriptor);
    }

    private MethodRef method(String name, String descriptor)
    {
        return new MethodRef(owner, namer.method(owner, name, descriptor), descriptor);
    }
}
