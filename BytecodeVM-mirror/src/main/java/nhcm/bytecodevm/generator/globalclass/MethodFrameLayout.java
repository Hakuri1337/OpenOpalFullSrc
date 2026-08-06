package nhcm.bytecodevm.generator.globalclass;

import nhcm.bytecodevm.utils.builder.FieldRef;
import nhcm.bytecodevm.utils.builder.MethodRef;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;

public class MethodFrameLayout
{
    public final String owner;
    private final GeneratedMemberNamer namer;

    public final FieldRef locals;
    public final FieldRef stack;
    public final FieldRef stackWords;
    public final FieldRef stackTypes;
    public final FieldRef stackWidths;
    public final FieldRef programCounter;
    public final FieldRef stateKey;
    public final FieldRef integrityKey;
    public final FieldRef blockIndex;
    public final FieldRef stackPointer;
    public final FieldRef returnValue;
    public final FieldRef returned;
    public final FieldRef mutableCode;
    public final FieldRef mutableMasks;
    public final FieldRef mutableProgram;

    public final MethodRef init;
    public final MethodRef push;
    public final MethodRef pushWithWidth;
    public final MethodRef pushInt;
    public final MethodRef pushLong;
    public final MethodRef pushFloat;
    public final MethodRef pushDouble;
    public final MethodRef pop;
    public final MethodRef popInt;
    public final MethodRef popLong;
    public final MethodRef popFloat;
    public final MethodRef popDouble;
    public final MethodRef peekWidth;
    public final MethodRef replaceIdentity;

    public MethodFrameLayout(String owner)
    {
        this(owner, GeneratedMemberNamer.DISABLED);
    }

    public MethodFrameLayout(String owner, GeneratedMemberNamer namer)
    {
        this.owner = owner;
        this.namer = namer;

        this.locals = field("locals", "[Ljava/lang/Object;");
        this.stack = field("stack", "[Ljava/lang/Object;");
        this.stackWords = field("stackWords", "[J");
        this.stackTypes = field("stackTypes", "[I");
        this.stackWidths = field("stackWidths", "[I");
        this.programCounter = field("programCounter", "I");
        this.stateKey = field("stateKey", "I");
        this.integrityKey = field("integrityKey", "I");
        this.blockIndex = field("blockIndex", "I");
        this.stackPointer = field("stackPointer", "I");
        this.returnValue = field("returnValue", "Ljava/lang/Object;");
        this.returned = field("returned", "Z");
        this.mutableCode = field("mutableCode", "[I");
        this.mutableMasks = field("mutableMasks", "[I");
        this.mutableProgram = field("mutableProgram", "Ljava/lang/Object;");

        this.init = method("<init>", "(II)V");
        this.push = method("push", "(Ljava/lang/Object;)V");
        this.pushWithWidth = method("push", "(Ljava/lang/Object;I)V");
        this.pushInt = method("pushInt", "(I)V");
        this.pushLong = method("pushLong", "(J)V");
        this.pushFloat = method("pushFloat", "(F)V");
        this.pushDouble = method("pushDouble", "(D)V");
        this.pop = method("pop", "()Ljava/lang/Object;");
        this.popInt = method("popInt", "()I");
        this.popLong = method("popLong", "()J");
        this.popFloat = method("popFloat", "()F");
        this.popDouble = method("popDouble", "()D");
        this.peekWidth = method("peekWidth", "()I");
        this.replaceIdentity = method("replaceIdentity", "(Ljava/lang/Object;Ljava/lang/Object;)V");
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
