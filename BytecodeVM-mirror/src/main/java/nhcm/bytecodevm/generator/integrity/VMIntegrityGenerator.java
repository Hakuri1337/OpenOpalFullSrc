package nhcm.bytecodevm.generator.integrity;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.InsnUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import nhcm.bytecodevm.utils.builder.InsnBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class VMIntegrityGenerator
{
    private static final int FNV_OFFSET = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;
    private static final int DERIVE_CHUNK_TARGETS = 32;

    private final ClassNode classNode;
    private final MethodNode deriveMethod;
    private final List<MethodNode> derivationMethods;
    private final VMIntegrityPlan plan;
    private final String hashMethodName;
    private final String stateFieldName;
    private final VMIntegrityPlan.CacheLayout cacheLayout;
    private final String probeMethodName;
    private final String probeBudgetFieldName;
    private final String probeCursorFieldName;
    private final int recheckInterval;
    private final int failMix;

    public VMIntegrityGenerator(
            String className,
            List<HashTarget> targets,
            int expectedCapability,
            double ratio,
            int recheckInterval,
            GeneratedMemberNamer namer)
    {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(namer, "namer");
        if (expectedCapability == 0)
        {
            throw new IllegalArgumentException("expectedCapability must be non-zero");
        }

        this.hashMethodName = namer.method(className, "hashResource", "(Ljava/lang/String;I)I");
        this.stateFieldName = namer.field(className, "STATE_WORD");
        this.cacheLayout = IntegrityCacheCodec.randomLayout();
        this.recheckInterval = targets.isEmpty() ? 0 : recheckInterval;
        this.probeMethodName = this.recheckInterval == 0
                ? null
                : namer.method(className, "pulseState", "()V");
        this.probeBudgetFieldName = this.recheckInterval == 0
                ? null
                : namer.field(className, "STATE_TICKET");
        this.probeCursorFieldName = this.recheckInterval == 0
                ? null
                : namer.field(className, "STATE_LANE");
        String deriveName = namer.method(className, "deriveIntegrityKey", "()I");
        String gatewayName = namer.method(className, "resolveState", "()I");
        String initializeName = namer.method(className, "initializeState", "()I");
        this.failMix = nonZeroRandom();
        this.classNode = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(classNode);
        classNode.fields.add(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.VOLATILE},
                stateFieldName,
                "J"));
        if (this.recheckInterval != 0)
        {
            classNode.fields.add(FieldUtils.newFieldNode(
                    new Acc[]{Acc.PRIVATE, Acc.STATIC},
                    probeBudgetFieldName,
                    "I"));
            classNode.fields.add(FieldUtils.newFieldNode(
                    new Acc[]{Acc.PRIVATE, Acc.STATIC},
                    probeCursorFieldName,
                    "I"));
        }
        classNode.methods.add(genClinitMethod(className));
        List<MethodNode> chunks = genDeriveChunks(className, deriveName, targets, namer);
        this.deriveMethod = genDeriveMethod(className, deriveName, chunks, expectedCapability);
        this.derivationMethods = new ArrayList<>(chunks.size() + 1);
        this.derivationMethods.add(deriveMethod);
        this.derivationMethods.addAll(chunks);
        classNode.methods.add(deriveMethod);
        classNode.methods.addAll(chunks);
        classNode.methods.add(genGatewayMethod(className, gatewayName, initializeName));
        classNode.methods.add(genInitializeMethod(className, initializeName, deriveName));
        if (this.recheckInterval != 0)
        {
            classNode.methods.add(genRuntimeProbeMethod(className, chunks));
        }
        classNode.methods.add(genHashMethod(className));
        this.plan = new VMIntegrityPlan(
                className,
                gatewayName,
                "()I",
                stateFieldName,
                cacheLayout,
                probeMethodName,
                expectedCapability,
                ratio);
    }

    public ClassNode classNode()
    {
        return classNode;
    }

    public MethodNode deriveMethod()
    {
        return deriveMethod;
    }

    public List<MethodNode> derivationMethods()
    {
        return List.copyOf(derivationMethods);
    }

    public VMIntegrityPlan plan()
    {
        return plan;
    }

    public static int hashBytes(byte[] bytes, int seed)
    {
        int acc = seed ^ FNV_OFFSET;
        for (byte value : bytes)
        {
            acc = (acc ^ (value & 0xFF)) * FNV_PRIME;
        }
        return acc;
    }

    private MethodNode genDeriveMethod(
            String owner,
            String name,
            List<MethodNode> chunks,
            int expectedCapability)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.STATIC}, name, "()I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local acc = ib.var("acc", "I");
        Local result = ib.var("result", "I");
        ib.set(acc, AdvInsnBuilder.constant(0));
        for (MethodNode chunk : chunks)
        {
            ib.set(acc, AdvInsnBuilder.callStatic(owner, chunk.name, "I", acc));
        }
        ib.ifElse(
                AdvInsnBuilder.equal(acc, AdvInsnBuilder.constant(0)),
                ok -> ok.set(result, AdvInsnBuilder.constant(expectedCapability)),
                fail -> fail.set(result, AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.constant(expectedCapability),
                        AdvInsnBuilder.bitOr(
                                AdvInsnBuilder.bitXor(
                                        AdvInsnBuilder.multiply(acc, AdvInsnBuilder.constant(failMix)),
                                        AdvInsnBuilder.unsignedShiftRight(acc, AdvInsnBuilder.constant(13))),
                                AdvInsnBuilder.constant(1)))));
        ib.returnValue(result);
        return method;
    }

    private MethodNode genGatewayMethod(String owner, String name, String initializeName)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.STATIC}, name, "()I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local envelope = ib.var("stateEnvelope", "J");
        ib.set(envelope, AdvInsnBuilder.staticField(owner, stateFieldName, "J"));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(envelope, AdvInsnBuilder.constant(0L)),
                hit -> hit.returnValue(IntegrityCacheCodec.emitDecode(
                        hit,
                        envelope,
                        cacheLayout,
                        "gateway")));
        ib.returnValue(AdvInsnBuilder.callStatic(owner, initializeName, "I"));
        return method;
    }

    private MethodNode genInitializeMethod(String owner, String name, String deriveName)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNCHRONIZED},
                name,
                "()I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local envelope = ib.var("stateEnvelope", "J");
        ib.set(envelope, AdvInsnBuilder.staticField(owner, stateFieldName, "J"));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(envelope, AdvInsnBuilder.constant(0L)),
                hit -> hit.returnValue(IntegrityCacheCodec.emitDecode(
                        hit,
                        envelope,
                        cacheLayout,
                        "locked")));
        Local result = ib.var("integrityKey", "I");
        ib.set(result, AdvInsnBuilder.callStatic(owner, deriveName, "I"));
        Local encodedEnvelope = IntegrityCacheCodec.emitEncode(
                ib,
                result,
                cacheLayout,
                "published");
        ib.set(AdvInsnBuilder.staticField(owner, stateFieldName, "J"), encodedEnvelope);
        ib.returnValue(result);
        return method;
    }

    private List<MethodNode> genDeriveChunks(
            String owner,
            String deriveName,
            List<HashTarget> targets,
            GeneratedMemberNamer namer)
    {
        List<MethodNode> chunks = new ArrayList<>();
        for (int from = 0, index = 0; from < targets.size(); from += DERIVE_CHUNK_TARGETS, index++)
        {
            int to = Math.min(targets.size(), from + DERIVE_CHUNK_TARGETS);
            String descriptor = "(I)I";
            String name = namer.method(owner, "deriveIntegrityChunk$" + index, descriptor);
            MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
            AdvInsnBuilder ib = new AdvInsnBuilder(method);
            Local acc = ib.getLocal("acc", "I", 0);
            for (int targetIndex = from; targetIndex < to; targetIndex++)
            {
                HashTarget target = targets.get(targetIndex);
                ib.set(acc, AdvInsnBuilder.bitOr(
                        acc,
                        AdvInsnBuilder.bitXor(
                                AdvInsnBuilder.callStatic(
                                        owner,
                                        hashMethodName,
                                        "I",
                                        AdvInsnBuilder.constant(target.resourceName()),
                                        AdvInsnBuilder.constant(target.seed())),
                                AdvInsnBuilder.constant(target.expectedHash()))));
            }
            ib.returnValue(acc);
            chunks.add(method);
        }
        if (chunks.isEmpty())
        {
            String descriptor = "(I)I";
            String name = namer.method(owner, "deriveIntegrityChunk$0", descriptor);
            MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
            AdvInsnBuilder ib = new AdvInsnBuilder(method);
            Local acc = ib.getLocal("acc", "I", 0);
            ib.returnValue(acc);
            chunks.add(method);
        }
        return List.copyOf(chunks);
    }

    private MethodNode genRuntimeProbeMethod(String owner, List<MethodNode> chunks)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                probeMethodName,
                "()V");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local budget = ib.var("stateTicket", "I");
        ib.set(budget, AdvInsnBuilder.minus(
                AdvInsnBuilder.staticField(owner, probeBudgetFieldName, "I"),
                AdvInsnBuilder.constant(1)));
        ib.set(AdvInsnBuilder.staticField(owner, probeBudgetFieldName, "I"), budget);
        ib.ifCondition(
                AdvInsnBuilder.greaterThan(budget, AdvInsnBuilder.constant(0)),
                hit -> hit.returnVoid());

        Local cursor = ib.var("stateLane", "I");
        Local nextCursor = ib.var("nextLane", "I");
        Local mismatch = ib.var("stateMismatch", "I");
        ib.set(cursor, AdvInsnBuilder.staticField(owner, probeCursorFieldName, "I"));
        ib.set(nextCursor, AdvInsnBuilder.plus(cursor, AdvInsnBuilder.constant(1)));
        ib.ifCondition(
                AdvInsnBuilder.not(AdvInsnBuilder.lessThan(
                        nextCursor,
                        AdvInsnBuilder.constant(chunks.size()))),
                wrap -> wrap.set(nextCursor, AdvInsnBuilder.constant(0)));
        ib.set(AdvInsnBuilder.staticField(owner, probeCursorFieldName, "I"), nextCursor);

        int jitterRange = Integer.highestOneBit(Math.max(1, recheckInterval / 4));
        int jitterMask = jitterRange - 1;
        int jitterMultiplier = nonZeroRandom() | 1;
        int jitterSalt = nonZeroRandom();
        Expr jitter = AdvInsnBuilder.bitAnd(
                AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.multiply(cursor, AdvInsnBuilder.constant(jitterMultiplier)),
                        AdvInsnBuilder.constant(jitterSalt)),
                AdvInsnBuilder.constant(jitterMask));
        ib.set(
                AdvInsnBuilder.staticField(owner, probeBudgetFieldName, "I"),
                AdvInsnBuilder.minus(AdvInsnBuilder.constant(recheckInterval), jitter));

        ib.set(mismatch, AdvInsnBuilder.constant(0));
        for (int index = 0; index < chunks.size(); index++)
        {
            MethodNode chunk = chunks.get(index);
            ib.ifCondition(
                    AdvInsnBuilder.equal(cursor, AdvInsnBuilder.constant(index)),
                    selected -> selected.set(mismatch, AdvInsnBuilder.callStatic(
                            owner,
                            chunk.name,
                            "I",
                            AdvInsnBuilder.constant(0))));
        }

        int poisonMultiplier = nonZeroRandom() | 1;
        int poisonSalt = nonZeroRandom();
        ib.ifCondition(
                AdvInsnBuilder.notEqual(mismatch, AdvInsnBuilder.constant(0)),
                failed -> {
                    Local envelope = failed.var("failedEnvelope", "J");
                    failed.set(envelope, AdvInsnBuilder.staticField(owner, stateFieldName, "J"));
                    Expr poison = AdvInsnBuilder.bitOr(
                            AdvInsnBuilder.shiftLeft(
                                    AdvInsnBuilder.cast(mismatch, "J"),
                                    AdvInsnBuilder.constant(32)),
                            AdvInsnBuilder.bitAnd(
                                    AdvInsnBuilder.cast(
                                            AdvInsnBuilder.bitXor(
                                                    AdvInsnBuilder.multiply(
                                                            mismatch,
                                                            AdvInsnBuilder.constant(poisonMultiplier)),
                                                    AdvInsnBuilder.constant(poisonSalt)),
                                            "J"),
                                    AdvInsnBuilder.constant(0xFFFF_FFFFL)));
                    failed.set(
                            AdvInsnBuilder.staticField(owner, stateFieldName, "J"),
                            AdvInsnBuilder.bitXor(envelope, poison));
                });
        ib.returnVoid();
        return method;
    }

    private MethodNode genClinitMethod(String owner)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.set(AdvInsnBuilder.staticField(owner, stateFieldName, "J"), AdvInsnBuilder.constant(0L));
        if (recheckInterval != 0)
        {
            ib.set(
                    AdvInsnBuilder.staticField(owner, probeBudgetFieldName, "I"),
                    AdvInsnBuilder.constant(Math.max(1, recheckInterval / 2)));
            ib.set(
                    AdvInsnBuilder.staticField(owner, probeCursorFieldName, "I"),
                    AdvInsnBuilder.constant(0));
        }
        ib.returnVoid();
        return method;
    }

    private MethodNode genHashMethod(String owner)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                hashMethodName,
                "(Ljava/lang/String;I)I");
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                new LabelNode(),
                new LabelNode(),
                new LabelNode(),
                "java/lang/Throwable"));

        LabelNode tryStart = method.tryCatchBlocks.getFirst().start;
        LabelNode tryEnd = method.tryCatchBlocks.getFirst().end;
        LabelNode handler = method.tryCatchBlocks.getFirst().handler;
        LabelNode missing = new LabelNode();
        LabelNode readLoop = new LabelNode();
        LabelNode readDone = new LabelNode();
        LabelNode byteLoop = new LabelNode();
        LabelNode byteDone = new LabelNode();
        LabelNode done = new LabelNode();

        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.label(tryStart);

        ib.pushClass(Type.getObjectType(owner));
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        ib.aload(0);
        ib.invokeVirtual("java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;");
        ib.astore(2);
        ib.aload(2);
        ib.ifNull(missing);

        ib.pushInt(1024);
        ib.newArray(org.objectweb.asm.Opcodes.T_BYTE);
        ib.astore(3);

        ib.iload(1);
        ib.pushInt(FNV_OFFSET);
        ib.ixor();
        ib.istore(4);

        ib.label(readLoop);
        ib.aload(2);
        ib.aload(3);
        ib.invokeVirtual("java/io/InputStream", "read", "([B)I");
        ib.istore(5);
        ib.iload(5);
        ib.pushInt(-1);
        ib.ifIcmpEq(readDone);

        ib.pushInt(0);
        ib.istore(6);
        ib.label(byteLoop);
        ib.iload(6);
        ib.iload(5);
        ib.ifIcmpGe(byteDone);
        ib.iload(4);
        ib.aload(3);
        ib.iload(6);
        ib.baload();
        ib.pushInt(255);
        ib.iand();
        ib.ixor();
        ib.pushInt(FNV_PRIME);
        ib.imul();
        ib.istore(4);
        ib.iinc(6, 1);
        ib.goto_(byteLoop);

        ib.label(byteDone);
        ib.goto_(readLoop);

        ib.label(readDone);
        ib.aload(2);
        ib.invokeVirtual("java/io/InputStream", "close", "()V");
        ib.iload(4);
        ib.istore(7);
        ib.goto_(done);

        ib.label(missing);
        ib.iload(1);
        ib.pushInt(nonZeroRandom());
        ib.ixor();
        ib.istore(7);
        ib.goto_(done);

        ib.label(done);
        ib.label(tryEnd);
        ib.iload(7);
        ib.ireturn();

        ib.label(handler);
        ib.pop();
        ib.iload(1);
        ib.pushInt(nonZeroRandom());
        ib.ixor();
        ib.ireturn();
        return method;
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

    public record HashTarget(String resourceName, int seed, int expectedHash)
    {
    }
}
