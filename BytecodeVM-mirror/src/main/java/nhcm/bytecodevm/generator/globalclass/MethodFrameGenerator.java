package nhcm.bytecodevm.generator.globalclass;

import lombok.Getter;
import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.FieldAccess;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.abstracts.ClassObj;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.builder.FieldRef;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class MethodFrameGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    @Getter
    public final MethodFrameLayout layout;

    public MethodFrameGenerator(String className)
    {
        this(className, GeneratedMemberNamer.DISABLED);
    }

    public MethodFrameGenerator(String className, GeneratedMemberNamer namer) {
        this(className, namer, null);
    }

    public MethodFrameGenerator(String className, GeneratedMemberNamer namer, VMStructure structure) {
        super(className);
        this.layout = new MethodFrameLayout(className, namer);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC}, className);
        List<FieldNode> fields = cn.fields;
        List<FieldNode> layoutFields = new ArrayList<>();
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, layout.locals.name(), layout.locals.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, layout.stack.name(), layout.stack.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, layout.stackWords.name(), layout.stackWords.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, layout.stackTypes.name(), layout.stackTypes.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, layout.stackWidths.name(), layout.stackWidths.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.programCounter.name(), layout.programCounter.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.stateKey.name(), layout.stateKey.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.integrityKey.name(), layout.integrityKey.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.blockIndex.name(), layout.blockIndex.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.stackPointer.name(), layout.stackPointer.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.returnValue.name(), layout.returnValue.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.returned.name(), layout.returned.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.mutableCode.name(), layout.mutableCode.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.mutableMasks.name(), layout.mutableMasks.descriptor()));
        layoutFields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, layout.mutableProgram.name(), layout.mutableProgram.descriptor()));
        if (structure != null)
        {
            Collections.rotate(layoutFields, structure.ordinal() % layoutFields.size());
            if ((structure.ordinal() & 1) != 0)
            {
                Collections.reverse(layoutFields);
            }
        }
        fields.addAll(layoutFields);
        cn.methods.add(this.genConstructor());
        cn.methods.add(this.genPushMethod());
        cn.methods.add(this.genPushWithWidthMethod());
        cn.methods.add(this.genPushIntMethod());
        cn.methods.add(this.genPushLongMethod());
        cn.methods.add(this.genPushFloatMethod());
        cn.methods.add(this.genPushDoubleMethod());
        cn.methods.add(this.genPopMethod());
        cn.methods.add(this.genPopIntMethod());
        cn.methods.add(this.genPopLongMethod());
        cn.methods.add(this.genPopFloatMethod());
        cn.methods.add(this.genPopDoubleMethod());
        cn.methods.add(this.genPeekWidthMethod());
        cn.methods.add(this.genReplaceIdentityMethod());
        this.classNode = cn;
    }

    private MethodNode genConstructor() {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.init.name(), // <init>
                layout.init.descriptor() // (II)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local maxLocals = ib.getLocal("maxLocals", "I", 1);
        Local maxStack = ib.getLocal("maxStack", "I", 2);
        ib.callNoArgSuperConstructor("java/lang/Object");
        ib.set(locals(), AdvInsnBuilder.newArray("java/lang/Object", maxLocals));
        ib.set(stack(), AdvInsnBuilder.newArray("java/lang/Object", maxStack));
        ib.set(stackWords(), AdvInsnBuilder.newArray("long", maxStack));
        ib.set(stackTypes(), AdvInsnBuilder.newArray("int", maxStack));
        ib.set(stackWidths(), AdvInsnBuilder.newArray("int", maxStack));
        ib.set(stateKey(), AdvInsnBuilder.constant(0));
        ib.set(integrityKey(), AdvInsnBuilder.constant(0));
        ib.set(blockIndex(), AdvInsnBuilder.constant(-1));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPushMethod() {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.push.name(), // push
                layout.push.descriptor() // (Ljava/lang/Object;)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.directCall(AdvInsnBuilder.callVirtual(
                selfFrame(),
                AdvInsnBuilder.object(layout.owner),
                layout.pushWithWidth.name(),
                Type.VOID_TYPE,
                AdvInsnBuilder.local("value", "java/lang/Object", 1),
                AdvInsnBuilder.constant(1)));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPushWithWidthMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.pushWithWidth.name(), // push
                layout.pushWithWidth.descriptor() // (Ljava/lang/Object;I)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local element = ib.getLocal("element", "java/lang/Object", 1);
        Local width = ib.getLocal("width", "I", 2);

        ib.setArray(stack(), stackPointer(), element);
        ib.setArray(stackTypes(), stackPointer(), AdvInsnBuilder.constant(0));
        ib.setArray(stackWidths(), stackPointer(), width);
        ib.set(stackPointer(), AdvInsnBuilder.plus(stackPointer(), AdvInsnBuilder.constant(1)));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPopMethod() {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.pop.name(), // pop
                layout.pop.descriptor() // ()Ljava/lang/Object;
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local slot = ib.var("slot", "I");
        ib.set(slot, AdvInsnBuilder.minus(stackPointer(), AdvInsnBuilder.constant(1)));

        Local valueType = ib.var("valueType", "I");
        ib.set(valueType, AdvInsnBuilder.arrayAt(stackTypes(), slot));

        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(0));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(0));

        Local objectValue = ib.var("objectValue", "java/lang/Object");
        ib.set(objectValue, AdvInsnBuilder.arrayAt(stack(), slot));
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));

        Expr target = AdvInsnBuilder.arrayAt(stackWords(), slot);

        ib.ifCondition(
                AdvInsnBuilder.equal(valueType, AdvInsnBuilder.constant(1)),
                b -> b.returnValue(AdvInsnBuilder.cast(target, "I"))
        );

        ib.ifCondition(
                AdvInsnBuilder.equal(valueType, AdvInsnBuilder.constant(2)),
                b -> b.returnValue(target)
        );

        ib.ifCondition(
                AdvInsnBuilder.equal(valueType, AdvInsnBuilder.constant(3)),
                b -> {
                    b.returnValue(AdvInsnBuilder.callStatic(
                            "java/lang/Float",
                            "intBitsToFloat",
                            "F",
                            AdvInsnBuilder.cast(target, "I")
                    ));
                }
        );

        ib.ifCondition(
                AdvInsnBuilder.equal(valueType, AdvInsnBuilder.constant(4)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Double",
                        "longBitsToDouble",
                        "D",
                        target
                ))
        );

        ib.returnValue(objectValue);

        return method;
    }

    private MethodNode genPushIntMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.pushInt.name(), // pushInt
                layout.pushInt.descriptor() // (I)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "I", 1);
        FieldAccess slot = stackPointer();
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));
        ib.setArray(stackWords(), slot, value);
        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(1));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(1));
        ib.set(stackPointer(), AdvInsnBuilder.plus(stackPointer(), AdvInsnBuilder.constant(1)));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPushLongMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.pushLong.name(), // pushLong
                layout.pushLong.descriptor() // (J)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "J", 1);
        FieldAccess slot = stackPointer();
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));
        ib.setArray(stackWords(), slot, value);
        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(2));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(2));
        ib.set(stackPointer(), AdvInsnBuilder.plus(slot, AdvInsnBuilder.constant(1)));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPushFloatMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.pushFloat.name(), // pushFloat
                layout.pushFloat.descriptor() // (F)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "F", 1);
        FieldAccess slot = stackPointer();
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));
        ib.setArray(stackWords(), slot, AdvInsnBuilder.callStatic(
                "java/lang/Float",
                "floatToRawIntBits",
                "I",
                value
        ));
        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(3));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(1));
        ib.set(stackPointer(), AdvInsnBuilder.plus(stackPointer(), AdvInsnBuilder.constant(1)));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPushDoubleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.pushDouble.name(), // pushDouble
                layout.pushDouble.descriptor() // (D)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "D", 1);
        FieldAccess slot = stackPointer();
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));
        ib.setArray(stackWords(), slot, AdvInsnBuilder.callStatic(
                "java/lang/Double",
                "doubleToRawLongBits",
                "J",
                value
        ));
        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(4));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(2));
        ib.set(stackPointer(), AdvInsnBuilder.plus(stackPointer(), AdvInsnBuilder.constant(1)));
        ib.returnVoid();
        return method;
    }

    private MethodNode genPopIntMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.popInt.name(), // popInt
                layout.popInt.descriptor() // ()I
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local slot = ib.var("slot", "I");
        ib.set(slot, AdvInsnBuilder.minus(stackPointer(), AdvInsnBuilder.constant(1)));
        Local type = ib.var("type", "I");
        ib.set(type, AdvInsnBuilder.arrayAt(stackTypes(), slot));

        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(0));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(0));

        ib.ifCondition(
                AdvInsnBuilder.equal(type, AdvInsnBuilder.constant(1)),
                b -> b.returnValue(AdvInsnBuilder.cast(AdvInsnBuilder.arrayAt(stackWords(), slot), "I"))
        );

        Local value = ib.var("value", "java/lang/Object");
        ib.set(value, AdvInsnBuilder.arrayAt(stack(), slot));

        ib.ifCondition(
                AdvInsnBuilder.isInstanceOf(value, "java/lang/Boolean"),
                b -> b.ifElse(
                        AdvInsnBuilder.equal(AdvInsnBuilder.cast(value, "java/lang/Boolean"), AdvInsnBuilder.constant(true)),
                        b1 -> b1.returnValue(AdvInsnBuilder.constant(1)),
                        b1 -> b1.returnValue(AdvInsnBuilder.constant(0))
                )
        );

        ib.ifCondition(
                AdvInsnBuilder.isInstanceOf(value, "java/lang/Character"),
                b -> b.returnValue(AdvInsnBuilder.cast(value, "C"))
        );

        ib.returnValue(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.cast(value, "java/lang/Number"),
                "java/lang/Number",
                "intValue",
                "I"
        ));
        return method;
    }

    private MethodNode genPopLongMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.popLong.name(), // popLong
                layout.popLong.descriptor() // ()J
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local slot = ib.var("slot", "I");
        ib.set(slot, AdvInsnBuilder.minus(stackPointer(), AdvInsnBuilder.constant(1)));
        Local type = ib.var("type", "I");
        ib.set(type, AdvInsnBuilder.arrayAt(stackTypes(), slot));

        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(0));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(0));

        ib.ifCondition(
                AdvInsnBuilder.equal(type, AdvInsnBuilder.constant(2)),
                b -> b.returnValue(AdvInsnBuilder.arrayAt(stackWords(), slot))
        );

        Local value = ib.var("value", "java/lang/Object");
        ib.set(value, AdvInsnBuilder.arrayAt(stack(), slot));
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));

        ib.returnValue(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.cast(value, "java/lang/Number"),
                "java/lang/Number",
                "longValue",
                "J"
        ));

        return method;
    }

    private MethodNode genPopFloatMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.popFloat.name(), // popFloat
                layout.popFloat.descriptor() // ()F
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local slot = ib.var("slot", "I");
        ib.set(slot, AdvInsnBuilder.minus(stackPointer(), AdvInsnBuilder.constant(1)));
        Local type = ib.var("type", "I");
        ib.set(type, AdvInsnBuilder.arrayAt(stackTypes(), slot));

        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(0));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(0));

        ib.ifCondition(
                AdvInsnBuilder.equal(type, AdvInsnBuilder.constant(3)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Float",
                        "intBitsToFloat",
                        "(I)F",
                        AdvInsnBuilder.cast(AdvInsnBuilder.arrayAt(stackWords(), slot), "I")
                ))
        );

        Local value = ib.var("value", "java/lang/Object");
        ib.set(value, AdvInsnBuilder.arrayAt(stack(), slot));
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));

        ib.returnValue(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.cast(value, "java/lang/Number"),
                "java/lang/Number",
                "floatValue",
                "F"
        ));
        return method;
    }

    private MethodNode genPopDoubleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.popDouble.name(), // popDouble
                layout.popDouble.descriptor() // ()D
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local slot = ib.var("slot", "I");
        ib.set(slot, AdvInsnBuilder.minus(stackPointer(), AdvInsnBuilder.constant(1)));
        Local type = ib.var("type", "I");
        ib.set(type, AdvInsnBuilder.arrayAt(stackTypes(), slot));

        ib.setArray(stackTypes(), slot, AdvInsnBuilder.constant(0));
        ib.setArray(stackWidths(), slot, AdvInsnBuilder.constant(0));

        ib.ifCondition(
                AdvInsnBuilder.equal(type, AdvInsnBuilder.constant(4)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Double",
                        "longBitsToDouble",
                        "(J)D",
                        AdvInsnBuilder.arrayAt(stackWords(), slot)
                ))
        );

        Local value = ib.var("value", "java/lang/Object");
        ib.set(value, AdvInsnBuilder.arrayAt(stack(), slot));
        ib.setArray(stack(), slot, AdvInsnBuilder.constant(null));

        ib.returnValue(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.cast(value, "java/lang/Number"),
                "java/lang/Number",
                "doubleValue",
                "D"
        ));
        return method;
    }

    private MethodNode genPeekWidthMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.peekWidth.name(),
                layout.peekWidth.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.arrayAt(stackWidths(), AdvInsnBuilder.minus(stackPointer(), AdvInsnBuilder.constant(1))));
        return method;
    }

    private MethodNode genReplaceIdentityMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.replaceIdentity.name(), // replaceIdentity
                layout.replaceIdentity.descriptor() // (Ljava/lang/Object;Ljava/lang/Object;)V
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local index = ib.var("index", "I");
        Local input1 = ib.getLocal("input1","Ljava/lang/Object;", 1);
        Local input2 = ib.getLocal("input2","Ljava/lang/Object;", 2);
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(locals())),
                b -> b.increment(index, 1),
                b -> {
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(AdvInsnBuilder.arrayAt(locals(), index), input1),
                            AdvInsnBuilder::continueLoop
                    );
                    b.setArray(locals(), index, input2);
                }
        );
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, stackPointer()),
                b -> b.increment(index, 1),
                b -> {
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(AdvInsnBuilder.arrayAt(stack(), index), input1),
                            AdvInsnBuilder::continueLoop
                    );
                    b.setArray(stack(), index, input2);
                }
        );
        ib.returnVoid();
        return method;
    }

    private Expr selfFrame()
    {
        return AdvInsnBuilder.self(layout.owner);
    }

    private FieldAccess frameField(FieldRef field)
    {
        return AdvInsnBuilder.field(selfFrame(), field);
    }

    private FieldAccess stackPointer()
    {
        return frameField(layout.stackPointer);
    }

    private FieldAccess stateKey()
    {
        return frameField(layout.stateKey);
    }

    private FieldAccess integrityKey()
    {
        return frameField(layout.integrityKey);
    }

    private FieldAccess blockIndex()
    {
        return frameField(layout.blockIndex);
    }

    private FieldAccess locals()
    {
        return frameField(layout.locals);
    }

    private FieldAccess stack()
    {
        return frameField(layout.stack);
    }

    private FieldAccess stackWidths()
    {
        return frameField(layout.stackWidths);
    }

    private FieldAccess stackTypes()
    {
        return frameField(layout.stackTypes);
    }

    private FieldAccess stackWords()
    {
        return frameField(layout.stackWords);
    }
}
