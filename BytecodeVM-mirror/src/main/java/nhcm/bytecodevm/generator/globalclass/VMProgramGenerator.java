package nhcm.bytecodevm.generator.globalclass;

import lombok.Getter;
import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.abstracts.ClassObj;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VMProgramGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    @Getter
    public final VMProgramLayout layout;

    public VMProgramGenerator(String className)
    {
        this(className, GeneratedMemberNamer.DISABLED);
    }

    public VMProgramGenerator(String className, GeneratedMemberNamer namer)
    {
        super(className);
        this.layout = new VMProgramLayout(className, namer);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.opcodeStreamField.name(), layout.opcodeStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.operandStreamField.name(), layout.operandStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.layoutStreamField.name(), layout.layoutStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.blockStreamField.name(), layout.blockStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.constantsField.name(), layout.constantsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.exceptionHandlersField.name(), layout.exceptionHandlersField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.opcodeMapField.name(), layout.opcodeMapField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.methodKeyField.name(), layout.methodKeyField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.featureFlagsField.name(), layout.featureFlagsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.maxLocalsField.name(), layout.maxLocalsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.maxStackField.name(), layout.maxStackField.descriptor()));
        cn.methods.add(genConstructor());
        cn.methods.add(genObjectGetter(layout.opcodeStream, layout.opcodeStreamField));
        cn.methods.add(genObjectGetter(layout.operandStream, layout.operandStreamField));
        cn.methods.add(genObjectGetter(layout.layoutStream, layout.layoutStreamField));
        cn.methods.add(genObjectGetter(layout.blockStream, layout.blockStreamField));
        cn.methods.add(genObjectGetter(layout.constants, layout.constantsField));
        cn.methods.add(genObjectGetter(layout.exceptionHandlers, layout.exceptionHandlersField));
        cn.methods.add(genObjectGetter(layout.opcodeMap, layout.opcodeMapField));
        cn.methods.add(genIntGetter(layout.methodKey, layout.methodKeyField));
        cn.methods.add(genIntGetter(layout.featureFlags, layout.featureFlagsField));
        cn.methods.add(genIntGetter(layout.maxLocals, layout.maxLocalsField));
        cn.methods.add(genIntGetter(layout.maxStack, layout.maxStackField));
        this.classNode = cn;
    }

    private MethodNode genConstructor()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.init.name(), // <init>
                layout.init.descriptor()
        );

        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local opcodeStream = ib.getLocal("opcodeStream", "[I", 1);
        Local operandStream = ib.getLocal("operandStream", "[I", 2);
        Local layoutStream = ib.getLocal("layoutStream", "[I", 3);
        Local blockStream = ib.getLocal("blockStream", "[I", 4);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 5);
        Local exceptionHandlers = ib.getLocal("exceptionHandlers", "[I", 6);
        Local opcodeMap = ib.getLocal("opcodeMap", "[I", 7);
        Local methodKey = ib.getLocal("methodKey", "I", 8);
        Local featureFlags = ib.getLocal("featureFlags", "I", 9);
        Local maxLocals = ib.getLocal("maxLocals", "I", 10);
        Local maxStack = ib.getLocal("maxStack", "I", 11);

        ib.callNoArgSuperConstructor("java/lang/Object");
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.opcodeStreamField), opcodeStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.operandStreamField), operandStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.layoutStreamField), layoutStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.blockStreamField), blockStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.constantsField), constants);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.exceptionHandlersField), exceptionHandlers);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.opcodeMapField), opcodeMap);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.methodKeyField), methodKey);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.featureFlagsField), featureFlags);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.maxLocalsField), maxLocals);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.maxStackField), maxStack);
        ib.returnVoid();
        return method;
    }

    private MethodNode genObjectGetter(nhcm.bytecodevm.utils.builder.MethodRef getter, nhcm.bytecodevm.utils.builder.FieldRef field)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                getter.name(),
                getter.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), field));
        return method;
    }

    private MethodNode genIntGetter(nhcm.bytecodevm.utils.builder.MethodRef getter, nhcm.bytecodevm.utils.builder.FieldRef field)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                getter.name(),
                getter.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), field));
        return method;
    }
}
