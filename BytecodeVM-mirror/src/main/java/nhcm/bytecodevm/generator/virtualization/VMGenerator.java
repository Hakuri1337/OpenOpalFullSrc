package nhcm.bytecodevm.generator.virtualization;

import lombok.Getter;
import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Condition;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.abstracts.ClassObj;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.generator.globalclass.*;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionRegistry;
import nhcm.bytecodevm.generator.virtualization.structure.LoweredInstructionPlanner;
import nhcm.bytecodevm.generator.virtualization.structure.VMStructurePlan;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGeneratorFactory;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMKernelShape;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchTarget;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.ArrayLengthBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.LoadArrayBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.NewArrayBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.StoreArrayBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.constant.*;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.control.*;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.conversion.CompareBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.conversion.ConvertBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.field.ReadFieldBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.field.WriteFieldBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.invoke.InvokeNormalBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local.IncrementBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local.LoadLocalBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local.StoreLocalBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lowered.DataFlowRegionBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lowered.RegisterOperationBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.*;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.object.CastBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.object.NewObjectBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack.DuplicateBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack.PopBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack.SwapBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lock.MonitorBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FileUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.InsnUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Consumer;

public class VMGenerator extends ClassObj
{
    private static final int MAX_INTERPRET_CHUNK_OPCODES = 8;
    private static final int INTERPRET_CHUNK_CODE_SIZE_LIMIT = 48_000;
    private static final int MAX_SUPER_INSTRUCTION_CHUNK_RECIPES = 8;
    private static final int SUPER_INSTRUCTION_CHUNK_CODE_SIZE_LIMIT = 48_000;

    private static final Map<Opcs, InterpretBranch> branches = new EnumMap<>(Opcs.class);

    static
    {
        registerBranches();
        validateBranches();
    }

    private static void registerBranches()
    {
        List<InterpretBranch> array = List.of(
                new ArrayLengthBranch(),
                new LoadArrayBranch(),
                new NewArrayBranch(),
                new StoreArrayBranch()
        );
        List<InterpretBranch> constant = List.of(
                new LoadConstantBranch(),
                new NopBranch(),
                new PushDoubleBranch(),
                new PushFloatBranch(),
                new PushIntBranch(),
                new PushLongBranch(),
                new PushNullBranch()
        );
        List<InterpretBranch> control = List.of(
                new FlowBranch(),
                new GotoBranch(),
                new InstanceofBranch(),
                new MonitorBranch(),
                new ReturnBranch(),
                new SwitchBranch(),
                new ThrowBranch()
        );
        List<InterpretBranch> conversion = List.of(
                new CompareBranch(),
                new ConvertBranch()
        );
        List<InterpretBranch> field = List.of(
                new ReadFieldBranch(),
                new WriteFieldBranch()
        );
        List<InterpretBranch> invoke = List.of(
                //new InvokeDynamicBranch(),
                new InvokeNormalBranch()
        );
        List<InterpretBranch> local = List.of(
                new IncrementBranch(),
                new LoadLocalBranch(),
                new StoreLocalBranch()
        );
        List<InterpretBranch> lowered = List.of(
                new RegisterOperationBranch(),
                new DataFlowRegionBranch()
        );
        List<InterpretBranch> math = List.of(
                new AddBranch(),
                new BitwiseAndBranch(),
                new BitwiseOrBranch(),
                new BitwiseXorBranch(),
                new DivideBranch(),
                new MultiplyBranch(),
                new NegateBranch(),
                new RemainderBranch(),
                new ShiftLeftBranch(),
                new ShiftRightBranch(),
                new SubtractBranch(),
                new UnsignedShiftRightBranch()
        );
        List<InterpretBranch> object = List.of(
                new CastBranch(),
                new NewObjectBranch()
        );
        List<InterpretBranch> stack = List.of(
                new DuplicateBranch(),
                new PopBranch(),
                new SwapBranch()
        );
        array.forEach(VMGenerator::register);
        constant.forEach(VMGenerator::register);
        control.forEach(VMGenerator::register);
        conversion.forEach(VMGenerator::register);
        field.forEach(VMGenerator::register);
        invoke.forEach(VMGenerator::register);
        local.forEach(VMGenerator::register);
        lowered.forEach(VMGenerator::register);
        math.forEach(VMGenerator::register);
        object.forEach(VMGenerator::register);
        stack.forEach(VMGenerator::register);
    }

    @Getter
    public final ClassNode classNode;

    @Getter
    private final List<CodePoolGenerator> codePoolGenerators;
    private final OpcMutator opcMutator;

    private final MethodFrameGenerator methodFrameGenerator;
    private final VMProgramGenerator vmProgramGenerator;
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final MethodFrameLayout frameLayout;
    private final VMProgramLayout programLayout;
    private final VMRuntimeLayout vmLayout;
    private final BytecodeVMConfig config;
    private final GeneratedMemberNamer namer;
    private final VMObfProfile profile;
    private final SuperInstructionRegistry superInstructions;
    private final int integrityCapability;
    private final VMStructurePlan structurePlan;
    private final VMStructureGenerator structureGenerator;
    private final VMStructureGenerationContext structureGeneration;
    private final Map<Integer, String> interpretChunkNames = new HashMap<>();
    private final Map<Integer, String> superInstructionChunkNames = new HashMap<>();
    private List<SuperInstructionChunk> superInstructionChunks = List.of();
    @Getter
    private final List<ClassNode> auxiliaryClasses = new ArrayList<>();

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, GeneratedMemberNamer.DISABLED);
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, namer, VMObfProfile.random());
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, namer, profile, new SuperInstructionRegistry(config.superInstructionMaxHandlers));
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions)
    {
        this(
                className,
                codePoolGenerators,
                opcMutator,
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config,
                namer,
                profile,
                superInstructions,
                0);
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions,
            int integrityCapability)
    {
        super(className);
        this.codePoolGenerators = List.copyOf(codePoolGenerators);
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.frameLayout = methodFrameGenerator.getLayout();
        this.programLayout = vmProgramGenerator.getLayout();
        this.vmLayout = new VMRuntimeLayout(
                className,
                methodFrameGenerator.descriptor(),
                vmProgramGenerator.descriptor(),
                namer,
                config.vmStructure);
        this.config = config;
        this.namer = namer;
        this.profile = Objects.requireNonNull(profile, "profile");
        this.superInstructions = Objects.requireNonNull(superInstructions, "superInstructions");
        this.integrityCapability = integrityCapability;
        this.structurePlan = VMStructurePlan.forStructure(config.vmStructure);
        this.structureGenerator = VMStructureGeneratorFactory.create(config.vmStructure);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(cn);
        this.classNode = cn;
        this.structureGeneration = new VMStructureGenerationContext(
                className(),
                cn,
                frameLayout,
                programLayout,
                vmLayout,
                profile,
                structurePlan,
                this::stepCall,
                this::interpretContext,
                this::structureClassName,
                this::structureMethodName,
                namer::field,
                this::schedulerDescriptor,
                ignored -> coroutineDescriptor(),
                this::mixCall,
                auxiliaryClasses);
        String vmCodePoolSign = vmCodePoolGenerator.descriptor();
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.codePools.name(), vmLayout.codePools.descriptor(), "Ljava/util/List<" + vmCodePoolSign + ">;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.fieldHandles.name(), vmLayout.fieldHandles.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.methodHandles.name(), vmLayout.methodHandles.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.methodTypes.name(), vmLayout.methodTypes.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodType;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.monitors.name(), vmLayout.monitors.descriptor(), "Ljava/util/Map<Ljava/lang/Object;Ljava/util/concurrent/locks/ReentrantLock;>;"));
        MethodNode interpretStepMethod = genInterpretStepMethod();
        cn.methods.add(genClInitMethod(codePoolGenerators));
        cn.methods.add(genExecuteMethod());
        cn.methods.add(genExecuteWithIntegrityMethod());
        cn.methods.add(genExecuteSegmentedMethod());
        cn.methods.add(genExecuteSegmentedWithIntegrityMethod());
        cn.methods.add(interpretStepMethod);
        cn.methods.add(genInterpretMethod());
        cn.methods.add(genInstructionIndexMethod());
        if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
        {
            cn.methods.add(genDecodeOpcodeMethod());
            cn.methods.add(genDecodeNextPcMethod());
            cn.methods.add(genDecodeOriginalPcMethod());
            cn.methods.add(genDecodeOperandMethod());
        }
        cn.methods.add(genMixMethod());
        cn.methods.add(genLayoutValueMethod());
        cn.methods.add(genBlockValueMethod());
        cn.methods.add(genStateKeyMethod());
        cn.methods.add(genInstructionIndexInBlockMethod());
        cn.methods.add(genSyncStateMethod());
        cn.methods.add(genDispatchKeyMethod());
        cn.methods.add(genResolveMethod());
        cn.methods.add(genConstantStringMethod());
        cn.methods.add(genMethodTypeMethod());
        cn.methods.add(genResolveConstantMethod());
        cn.methods.add(genFindExceptionHandlerMethod());
        cn.methods.add(genGetFieldMethod());
        cn.methods.add(genSetFieldMethod());
        cn.methods.add(genFieldHandleMethod());
        cn.methods.add(genAdaptFieldHandleMethod());
        cn.methods.add(genUnsafeMethod());
        cn.methods.add(genUnsafeSetStaticFieldMethod());
        cn.methods.add(genFindFieldMethod());
        cn.methods.add(genFindMethodMethod());
        cn.methods.add(genInvokeMethod());
        cn.methods.add(genConstructMethod());
        cn.methods.add(genAdaptMethodHandleMethod());
        cn.methods.add(genAdaptDirectMethodHandleMethod());
        cn.methods.add(genAdaptConstructorHandleMethod());
        cn.methods.add(genCoerceArgumentMethod());
        cn.methods.add(genCloneArrayMethod());
        cn.methods.add(genLoadOwnerMethod());
        cn.methods.add(genLoadOwnerWithLoaderMethod());
        cn.methods.add(genMonitorForMethod());
        cn.methods.add(genMonitorEnterMethod());
        cn.methods.add(genMonitorExitMethod());
        cn.methods.add(genRethrowMethod());
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.REGISTER ||
            structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.DATA_FLOW)
        {
            cn.methods.add(genRegisterReadMethod());
            cn.methods.add(genRegisterWriteMethod());
            cn.methods.add(genExecuteRegisterOpMethod());
            if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.DATA_FLOW)
            {
                cn.methods.add(genExecuteDataFlowMethod());
            }
        }
    }

    private MethodNode genInterpretStepMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.interpretStep.name(),
                vmLayout.interpretStep.descriptor());
        LabelNode unknownOpcode = new LabelNode();
        LabelNode afterDispatch = new LabelNode();
        LabelNode batchStart = new LabelNode();
        LabelNode batchComplete = new LabelNode();
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode exceptionHandler = new LabelNode();
        LabelNode noHandler = new LabelNode();
        methodNode.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                tryStart,
                tryEnd,
                exceptionHandler,
                "java/lang/Throwable"));
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        InterpretContext context = interpretContext(afterDispatch);
        VMKernelShape kernelShape = structureGenerator.kernelShape();
        Local structureStateArgument = ib.getLocal("structureStateArgument", "I", 4);
        Local batchCount = context.intLocal("batchCount", InterpretContext.DISPATCH_SELECTOR + 8);
        ib.set(context.structureState(), structureStateArgument);
        if (kernelShape.exceptionTableMode() == VMKernelShape.ExceptionTableMode.EAGER)
        {
            emitLoadExceptionHandlers(ib, context);
        }
        ib.set(batchCount, AdvInsnBuilder.constant(0));
        ib.mark(batchStart, "batchStart");
        if (kernelShape.exceptionTableMode() == VMKernelShape.ExceptionTableMode.PER_STEP)
        {
            emitLoadExceptionHandlers(ib, context);
        }
        ib.ifCondition(
                AdvInsnBuilder.isTrue(context.frameReturned()),
                b -> b.returnValue(AdvInsnBuilder.constant(1)));
        ib.set(context.instructionPc(), context.frameProgramCounter());
        ib.set(context.originalPc(), context.instructionPc());
        ib.set(context.instructionIndex(), AdvInsnBuilder.constant(-1));
        ib.set(context.opcode(), AdvInsnBuilder.constant(0));

        ib.mark(tryStart, "tryStart");
        ib.set(context.instructionIndex(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndex.name(),
                "I",
                context.program(),
                context.frame(),
                context.instructionPc()));
        ib.ifCondition(
                AdvInsnBuilder.equal(context.instructionIndex(), AdvInsnBuilder.constant(-1)),
                b -> b.returnValue(AdvInsnBuilder.constant(1)));
        ib.set(context.operandIndex(), AdvInsnBuilder.constant(0));
        emitKernelDecode(ib, context, kernelShape);

        generateDispatch(
                ib,
                afterDispatch,
                unknownOpcode
        );
        ib.mark(afterDispatch, "afterDispatch");
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            emitSelfMutation(ib, context);
        }
        ib.ifCondition(
                AdvInsnBuilder.isFalse(context.frameReturned()),
                b -> b.directCall(AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.syncState.name(),
                        "V",
                        context.program(),
                        context.frame(),
                        context.frameProgramCounter())));
        ib.gotoLabel(batchComplete);
        ib.mark(tryEnd, "tryEnd");

        ib.mark(unknownOpcode, "unknownOpcode");
        generateUnknownOpcode(ib);

        ib.mark(exceptionHandler, "exceptionHandler");
        ib.storeTop(context.thrown());
        if (kernelShape.exceptionTableMode() == VMKernelShape.ExceptionTableMode.ON_THROW)
        {
            emitLoadExceptionHandlers(ib, context);
        }
        ib.set(context.handlerPc(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.findExceptionHandler.name(),
                "I",
                context.thrown(),
                context.exceptionHandlers(),
                context.originalPc(),
                context.instructionIndex(),
                context.opcode(),
                context.program(),
                context.frame(),
                context.constants()));
        ib.ifCondition(AdvInsnBuilder.equal(context.handlerPc(), AdvInsnBuilder.constant(-1)), b -> b.gotoLabel(noHandler));
        ib.set(context.frameField(frameLayout.stackPointer), AdvInsnBuilder.constant(0));
        ib.directCall(AdvInsnBuilder.callVirtual(
                context.frame(),
                frameLayout.owner,
                frameLayout.push.name(),
                "V",
                AdvInsnBuilder.cast(context.thrown(), "java/lang/Object")));
        ib.set(context.frameProgramCounter(), context.handlerPc());
        ib.directCall(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.syncState.name(),
                "V",
                context.program(),
                context.frame(),
                context.handlerPc()));
        ib.gotoLabel(batchComplete);

        ib.mark(noHandler, "noHandler");
        ib.throwValue(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.rethrow.name(),
                "java/lang/RuntimeException",
                context.thrown()));

        ib.mark(batchComplete, "batchComplete");
        ib.ifCondition(
                AdvInsnBuilder.isTrue(context.frameReturned()),
                b -> b.returnValue(AdvInsnBuilder.constant(1)));
        ib.increment(batchCount, 1);
        ib.ifCondition(
                AdvInsnBuilder.lessThan(batchCount, AdvInsnBuilder.constant(structureGenerator.stepBatchSize())),
                b -> b.gotoLabel(batchStart));
        ib.returnValue(AdvInsnBuilder.constant(0));
        return methodNode;
    }

    private void emitLoadExceptionHandlers(AdvInsnBuilder ib, InterpretContext context)
    {
        ib.set(context.exceptionHandlers(), AdvInsnBuilder.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.exceptionHandlers.name(),
                "[I"));
    }

    private void emitKernelDecode(
            AdvInsnBuilder ib,
            InterpretContext context,
            VMKernelShape shape)
    {
        Local decodedNextPc = context.intLocal("decodedNextPc", 84);
        Consumer<AdvInsnBuilder> opcode = code -> {
            if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
            {
                code.set(context.opcode(), AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.decodeOpcode.name(),
                        "I",
                        context.program(),
                        context.frame(),
                        context.instructionIndex()));
            }
            else
            {
                emitDecodeOpcodeInline(code, context, context.opcode());
            }
        };
        Consumer<AdvInsnBuilder> next = code -> {
            if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
            {
                code.set(decodedNextPc, AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.decodeNextPc.name(),
                        "I",
                        context.program(),
                        context.frame(),
                        context.instructionIndex()));
            }
            else
            {
                emitLayoutValueInline(
                        code,
                        context,
                        decodedNextPc,
                        ProtectedVMMethod.LAYOUT_NEXT_PC,
                        90);
            }
            code.set(context.frameProgramCounter(), decodedNextPc);
        };
        Consumer<AdvInsnBuilder> original = code -> {
            if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
            {
                code.set(context.originalPc(), AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.decodeOriginalPc.name(),
                        "I",
                        context.program(),
                        context.frame(),
                        context.instructionIndex()));
            }
            else
            {
                emitLayoutValueInline(
                        code,
                        context,
                        context.originalPc(),
                        ProtectedVMMethod.LAYOUT_ORIGINAL_PC,
                        94);
            }
        };

        switch (shape.decodeOrder())
        {
            case OPCODE_NEXT_ORIGINAL -> emitDecodeOrder(ib, opcode, next, original);
            case OPCODE_ORIGINAL_NEXT -> emitDecodeOrder(ib, opcode, original, next);
            case NEXT_OPCODE_ORIGINAL -> emitDecodeOrder(ib, next, opcode, original);
            case NEXT_ORIGINAL_OPCODE -> emitDecodeOrder(ib, next, original, opcode);
            case ORIGINAL_OPCODE_NEXT -> emitDecodeOrder(ib, original, opcode, next);
            case ORIGINAL_NEXT_OPCODE -> emitDecodeOrder(ib, original, next, opcode);
        }
    }

    @SafeVarargs
    private static void emitDecodeOrder(
            AdvInsnBuilder ib,
            Consumer<AdvInsnBuilder>... decoders)
    {
        for (Consumer<AdvInsnBuilder> decoder : decoders)
        {
            decoder.accept(ib);
        }
    }

    private MethodNode genInterpretMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.interpret.name(),
                vmLayout.interpret.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        InterpretContext context = interpretContext(null);
        ib.set(context.code(), AdvInsnBuilder.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.opcodeStream.name(),
                "[I"));
        ib.set(context.constants(), AdvInsnBuilder.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.constants.name(),
                "[Ljava/lang/Object;"));

        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            prepareMutableCode(ib, context);
        }

        structureGenerator.emitScheduler(structureGeneration, ib, context);
        ib.returnVoid();
        return method;
    }

    private MethodNode genRegisterReadMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.registerRead.name(),
                vmLayout.registerRead.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local baseStack = ib.getLocal("baseStack", "I", 2);
        Local token = ib.getLocal("token", "I", 3);
        Local encodedOffset = ib.getLocal("encodedOffset", "I", 4);
        Local offset = ib.getLocal("offset", "I", 5);
        Local slot = ib.getLocal("slot", "I", 6);
        Local type = ib.getLocal("type", "I", 7);

        ib.ifCondition(
                AdvInsnBuilder.greaterOrEqual(token, AdvInsnBuilder.constant(0)),
                local -> local.returnValue(AdvInsnBuilder.arrayAt(
                        AdvInsnBuilder.field(frame, frameLayout.locals),
                        token)));
        ib.set(encodedOffset, AdvInsnBuilder.bitAnd(token, AdvInsnBuilder.constant(Integer.MAX_VALUE)));
        ib.set(offset, AdvInsnBuilder.bitXor(
                AdvInsnBuilder.unsignedShiftRight(encodedOffset, AdvInsnBuilder.constant(1)),
                AdvInsnBuilder.minus(
                        AdvInsnBuilder.constant(0),
                        AdvInsnBuilder.bitAnd(encodedOffset, AdvInsnBuilder.constant(1)))));
        ib.set(slot, AdvInsnBuilder.plus(baseStack, offset));
        ib.set(type, AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stackTypes), slot));

        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] cases = new java.util.function.Consumer[5];
        cases[0] = b -> b.returnValue(AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stack), slot));
        cases[1] = b -> b.returnValue(NumericType.INT.box(AdvInsnBuilder.cast(
                AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stackWords), slot), "I")));
        cases[2] = b -> b.returnValue(NumericType.LONG.box(
                AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stackWords), slot)));
        cases[3] = b -> b.returnValue(NumericType.FLOAT.box(AdvInsnBuilder.callStatic(
                "java/lang/Float",
                "intBitsToFloat",
                "F",
                AdvInsnBuilder.cast(
                        AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stackWords), slot),
                        "I"))));
        cases[4] = b -> b.returnValue(NumericType.DOUBLE.box(AdvInsnBuilder.callStatic(
                "java/lang/Double",
                "longBitsToDouble",
                "D",
                AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stackWords), slot))));
        ib.switchTable(
                type,
                0,
                b -> b.returnValue(AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.stack), slot)),
                cases);
        ib.returnValue(AdvInsnBuilder.constant(null));
        return method;
    }

    private MethodNode genRegisterWriteMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.registerWrite.name(),
                vmLayout.registerWrite.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local baseStack = ib.getLocal("baseStack", "I", 2);
        Local token = ib.getLocal("token", "I", 3);
        Local value = ib.getLocal("value", "java/lang/Object", 4);
        Local width = ib.getLocal("width", "I", 5);
        Local encodedOffset = ib.getLocal("encodedOffset", "I", 6);
        Local offset = ib.getLocal("offset", "I", 7);
        Local slot = ib.getLocal("slot", "I", 8);

        ib.ifCondition(
                AdvInsnBuilder.greaterOrEqual(token, AdvInsnBuilder.constant(0)),
                local -> {
                    local.setArray(AdvInsnBuilder.field(frame, frameLayout.locals), token, value);
                    local.returnVoid();
                });
        ib.set(encodedOffset, AdvInsnBuilder.bitAnd(token, AdvInsnBuilder.constant(Integer.MAX_VALUE)));
        ib.set(offset, AdvInsnBuilder.bitXor(
                AdvInsnBuilder.unsignedShiftRight(encodedOffset, AdvInsnBuilder.constant(1)),
                AdvInsnBuilder.minus(
                        AdvInsnBuilder.constant(0),
                        AdvInsnBuilder.bitAnd(encodedOffset, AdvInsnBuilder.constant(1)))));
        ib.set(slot, AdvInsnBuilder.plus(baseStack, offset));
        ib.setArray(AdvInsnBuilder.field(frame, frameLayout.stack), slot, value);
        ib.setArray(AdvInsnBuilder.field(frame, frameLayout.stackTypes), slot, AdvInsnBuilder.constant(0));
        ib.setArray(AdvInsnBuilder.field(frame, frameLayout.stackWidths), slot, width);
        ib.returnVoid();
        return method;
    }

    private MethodNode genExecuteRegisterOpMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.executeRegisterOp.name(),
                vmLayout.executeRegisterOp.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        RegisterOperationContext operation = new RegisterOperationContext(
                ib.getLocal("program", programLayout.owner, 0),
                ib.getLocal("frame", frameLayout.owner, 1),
                ib.getLocal("constants", "[Ljava/lang/Object;", 2),
                ib.getLocal("semantic", "I", 3),
                ib.getLocal("baseStack", "I", 4),
                ib.getLocal("destination", "I", 5),
                ib.getLocal("sourceA", "I", 6),
                ib.getLocal("sourceB", "I", 7),
                ib.getLocal("auxiliary", "I", 8),
                ib.getLocal("width", "I", 9),
                ib.getLocal("instructionIndex", "I", 10),
                ib.getLocal("opcode", "I", 11));

        List<Opcs> operations = new ArrayList<>();
        for (Opcs opcode : Opcs.values())
        {
            if (isLoweredRegisterOpcode(opcode))
            {
                operations.add(opcode);
            }
        }
        emitRegisterDecisionTree(ib, operation, operations, 0, operations.size());
        return method;
    }

    private void emitRegisterDecisionTree(
            AdvInsnBuilder ib,
            RegisterOperationContext operation,
            List<Opcs> operations,
            int from,
            int to)
    {
        if (from >= to)
        {
            ib.throwValue(AdvInsnBuilder.newObject(
                    "java/lang/IllegalStateException",
                    stringConcat(
                            AdvInsnBuilder.constant("Unknown register semantic "),
                            operation.semantic)));
            return;
        }
        int middle = (from + to) >>> 1;
        Opcs opcode = operations.get(middle);
        ib.ifElse(
                AdvInsnBuilder.equal(
                        operation.semantic,
                        AdvInsnBuilder.constant(opcode.ordinal())),
                match -> {
                    emitRegisterOperation(match, operation, opcode);
                    match.returnVoid();
                },
                mismatch -> mismatch.ifElse(
                        AdvInsnBuilder.lessThan(
                                operation.semantic,
                                AdvInsnBuilder.constant(opcode.ordinal())),
                        lower -> emitRegisterDecisionTree(
                                lower,
                                operation,
                                operations,
                                from,
                                middle),
                        higher -> emitRegisterDecisionTree(
                                higher,
                                operation,
                                operations,
                                middle + 1,
                                to)));
    }

    private MethodNode genExecuteDataFlowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.executeDataFlow.name(),
                vmLayout.executeDataFlow.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 2);
        Local payload = ib.getLocal("payload", "[I", 3);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 4);
        Local opcode = ib.getLocal("opcode", "I", 5);
        Local nodeCount = ib.getLocal("nodeCount", "I", 6);
        Local finalDelta = ib.getLocal("finalDelta", "I", 7);
        Local baseStack = ib.getLocal("baseStack", "I", 8);
        Local doneMask = ib.getLocal("doneMask", "I", 9);
        Local targetMask = ib.getLocal("targetMask", "I", 10);
        Local progress = ib.getLocal("progress", "I", 11);
        Local node = ib.getLocal("node", "I", 12);
        Local offset = ib.getLocal("nodeOffset", "I", 13);
        Local dependencies = ib.getLocal("dependencies", "I", 14);
        Local bit = ib.getLocal("nodeBit", "I", 15);

        ib.set(nodeCount, AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.constant(0)));
        ib.set(finalDelta, AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.constant(1)));
        ib.set(baseStack, AdvInsnBuilder.field(frame, frameLayout.stackPointer));
        ib.set(doneMask, AdvInsnBuilder.constant(0));
        ib.set(targetMask, AdvInsnBuilder.minus(
                AdvInsnBuilder.shiftLeft(AdvInsnBuilder.constant(1), nodeCount),
                AdvInsnBuilder.constant(1)));
        ib.whileLoop(
                AdvInsnBuilder.notEqual(doneMask, targetMask),
                schedule -> {
                    schedule.set(progress, AdvInsnBuilder.constant(0));
                    schedule.forLoop(
                            b -> b.set(node, AdvInsnBuilder.constant(0)),
                            AdvInsnBuilder.lessThan(node, nodeCount),
                            b -> b.increment(node, 1),
                            ready -> {
                                ready.set(bit, AdvInsnBuilder.shiftLeft(AdvInsnBuilder.constant(1), node));
                                ready.ifCondition(
                                        AdvInsnBuilder.notEqual(
                                                AdvInsnBuilder.bitAnd(doneMask, bit),
                                                AdvInsnBuilder.constant(0)),
                                        AdvInsnBuilder::continueLoop);
                                ready.set(offset, AdvInsnBuilder.plus(
                                        AdvInsnBuilder.constant(LoweredInstructionPlanner.DATA_FLOW_HEADER_SIZE),
                                        AdvInsnBuilder.multiply(
                                                node,
                                                AdvInsnBuilder.constant(LoweredInstructionPlanner.DATA_FLOW_NODE_SIZE))));
                                ready.set(
                                        dependencies,
                                        AdvInsnBuilder.arrayAt(
                                                payload,
                                                AdvInsnBuilder.plus(
                                                        offset,
                                                        AdvInsnBuilder.constant(LoweredInstructionPlanner.REGISTER_PLAN_SIZE))));
                                ready.ifCondition(
                                        AdvInsnBuilder.notEqual(
                                                AdvInsnBuilder.bitAnd(
                                                        dependencies,
                                                        AdvInsnBuilder.bitXor(doneMask, AdvInsnBuilder.constant(-1))),
                                                AdvInsnBuilder.constant(0)),
                                        AdvInsnBuilder::continueLoop);
                                ready.directCall(AdvInsnBuilder.callStatic(
                                        vmLayout.owner,
                                        vmLayout.executeRegisterOp.name(),
                                        "V",
                                        program,
                                        frame,
                                        constants,
                                        AdvInsnBuilder.arrayAt(payload, offset),
                                        AdvInsnBuilder.plus(
                                                baseStack,
                                                AdvInsnBuilder.arrayAt(
                                                        payload,
                                                        AdvInsnBuilder.plus(offset, AdvInsnBuilder.constant(7)))),
                                        AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.plus(offset, AdvInsnBuilder.constant(1))),
                                        AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.plus(offset, AdvInsnBuilder.constant(2))),
                                        AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.plus(offset, AdvInsnBuilder.constant(3))),
                                        AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.plus(offset, AdvInsnBuilder.constant(4))),
                                        AdvInsnBuilder.arrayAt(payload, AdvInsnBuilder.plus(offset, AdvInsnBuilder.constant(6))),
                                        instructionIndex,
                                        opcode));
                                ready.set(doneMask, AdvInsnBuilder.bitOr(doneMask, bit));
                                ready.increment(progress, 1);
                            });
                    schedule.ifCondition(
                            AdvInsnBuilder.equal(progress, AdvInsnBuilder.constant(0)),
                            blocked -> blocked.throwValue(AdvInsnBuilder.newObject(
                                    "java/lang/IllegalStateException",
                                    AdvInsnBuilder.constant("Cyclic data-flow region"))));
                });
        ib.set(
                AdvInsnBuilder.field(frame, frameLayout.stackPointer),
                AdvInsnBuilder.plus(baseStack, finalDelta));
        ib.returnVoid();
        return method;
    }

    private boolean isLoweredRegisterOpcode(Opcs opcode)
    {
        return switch (opcode)
        {
            case NOP, ACONST_NULL,
                 ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1,
                 BIPUSH, SIPUSH, LDC,
                 ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
                 POP,
                 IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM,
                 INEG, LNEG, FNEG, DNEG,
                 ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR,
                 IAND, LAND, IOR, LOR, IXOR, LXOR,
                 IINC,
                 I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D,
                 D2I, D2L, D2F, I2B, I2C, I2S,
                 LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> true;
            default -> false;
        };
    }

    private void emitRegisterOperation(AdvInsnBuilder ib, RegisterOperationContext context, Opcs opcode)
    {
        switch (opcode)
        {
            case NOP, POP -> { }
            case ACONST_NULL -> registerWrite(ib, context, AdvInsnBuilder.constant(null));
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 ->
                    registerWrite(ib, context, NumericType.INT.box(AdvInsnBuilder.constant(opcode.opcode - org.objectweb.asm.Opcodes.ICONST_0)));
            case BIPUSH, SIPUSH -> registerWrite(ib, context, NumericType.INT.box(context.auxiliary));
            case LCONST_0, LCONST_1 -> registerWrite(
                    ib,
                    context,
                    NumericType.LONG.box(AdvInsnBuilder.constant((long) (opcode.opcode - org.objectweb.asm.Opcodes.LCONST_0))));
            case FCONST_0, FCONST_1, FCONST_2 -> registerWrite(
                    ib,
                    context,
                    NumericType.FLOAT.box(AdvInsnBuilder.constant((float) (opcode.opcode - org.objectweb.asm.Opcodes.FCONST_0))));
            case DCONST_0, DCONST_1 -> registerWrite(
                    ib,
                    context,
                    NumericType.DOUBLE.box(AdvInsnBuilder.constant((double) (opcode.opcode - org.objectweb.asm.Opcodes.DCONST_0))));
            case LDC -> {
                Local constant = ib.var("registerConstant", "java/lang/Object");
                ib.set(constant, AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.resolveConstant.name(),
                        "java/lang/Object",
                        context.program,
                        AdvInsnBuilder.arrayAt(context.constants, context.auxiliary),
                        context.frame,
                        context.instructionIndex,
                        context.opcode));
                ib.ifElse(
                        AdvInsnBuilder.or(
                                AdvInsnBuilder.isInstanceOf(constant, "java/lang/Long"),
                                AdvInsnBuilder.isInstanceOf(constant, "java/lang/Double")),
                        category2 -> registerWrite(category2, context, constant, AdvInsnBuilder.constant(2)),
                        category1 -> registerWrite(category1, context, constant, AdvInsnBuilder.constant(1)));
            }
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> registerWrite(
                    ib,
                    context,
                    registerRead(context, context.sourceA));
            case IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM,
                 IAND, LAND, IOR, LOR, IXOR, LXOR -> emitRegisterBinary(ib, context, opcode);
            case ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR -> emitRegisterShift(ib, context, opcode);
            case INEG, LNEG, FNEG, DNEG -> emitRegisterNegate(ib, context, opcode);
            case IINC -> registerWrite(
                    ib,
                    context,
                    NumericType.INT.box(AdvInsnBuilder.plus(
                            NumericType.INT.unbox(registerRead(context, context.sourceA)),
                            context.auxiliary)));
            case I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D,
                 D2I, D2L, D2F, I2B, I2C, I2S -> emitRegisterConversion(ib, context, opcode);
            case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> emitRegisterCompare(ib, context, opcode);
            default -> throw new IllegalArgumentException("Not a lowered register opcode: " + opcode);
        }
    }

    private void emitRegisterBinary(AdvInsnBuilder ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        Local left = ib.var("registerLeft" + opcode, type.descriptor());
        Local right = ib.var("registerRight" + opcode, type.descriptor());
        ib.set(left, type.unbox(registerRead(context, context.sourceA)));
        ib.set(right, type.unbox(registerRead(context, context.sourceB)));
        Expr value = switch (opcode)
        {
            case IADD, LADD, FADD, DADD -> AdvInsnBuilder.plus(left, right);
            case ISUB, LSUB, FSUB, DSUB -> AdvInsnBuilder.minus(left, right);
            case IMUL, LMUL, FMUL, DMUL -> AdvInsnBuilder.multiply(left, right);
            case IDIV, LDIV, FDIV, DDIV -> AdvInsnBuilder.divide(left, right);
            case IREM, LREM, FREM, DREM -> AdvInsnBuilder.remainder(left, right);
            case IAND, LAND -> AdvInsnBuilder.bitAnd(left, right);
            case IOR, LOR -> AdvInsnBuilder.bitOr(left, right);
            case IXOR, LXOR -> AdvInsnBuilder.bitXor(left, right);
            default -> throw new IllegalArgumentException("Not a register binary opcode: " + opcode);
        };
        registerWrite(ib, context, type.box(value));
    }

    private void emitRegisterShift(AdvInsnBuilder ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = opcode.name().charAt(0) == 'L' ? NumericType.LONG : NumericType.INT;
        Local left = ib.var("registerShiftLeft" + opcode, type.descriptor());
        Local right = ib.var("registerShiftRight" + opcode, "I");
        ib.set(left, type.unbox(registerRead(context, context.sourceA)));
        ib.set(right, NumericType.INT.unbox(registerRead(context, context.sourceB)));
        Expr value = switch (opcode)
        {
            case ISHL, LSHL -> AdvInsnBuilder.shiftLeft(left, right);
            case ISHR, LSHR -> AdvInsnBuilder.shiftRight(left, right);
            case IUSHR, LUSHR -> AdvInsnBuilder.unsignedShiftRight(left, right);
            default -> throw new IllegalArgumentException("Not a register shift opcode: " + opcode);
        };
        registerWrite(ib, context, type.box(value));
    }

    private void emitRegisterNegate(AdvInsnBuilder ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        registerWrite(
                ib,
                context,
                type.box(AdvInsnBuilder.minus(
                        AdvInsnBuilder.constant(switch (type)
                        {
                            case INT -> 0;
                            case LONG -> 0L;
                            case FLOAT -> 0.0F;
                            case DOUBLE -> 0.0D;
                        }),
                        type.unbox(registerRead(context, context.sourceA)))));
    }

    private void emitRegisterConversion(AdvInsnBuilder ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType source = switch (opcode.name().charAt(0))
        {
            case 'I' -> NumericType.INT;
            case 'L' -> NumericType.LONG;
            case 'F' -> NumericType.FLOAT;
            case 'D' -> NumericType.DOUBLE;
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
        NumericType target = switch (opcode.name().charAt(2))
        {
            case 'L' -> NumericType.LONG;
            case 'F' -> NumericType.FLOAT;
            case 'D' -> NumericType.DOUBLE;
            default -> NumericType.INT;
        };
        Expr sourceValue = source.unbox(registerRead(context, context.sourceA));
        Expr converted = switch (opcode)
        {
            case I2L, F2L, D2L -> AdvInsnBuilder.toLong(sourceValue);
            case I2F, L2F, D2F -> AdvInsnBuilder.toFloat(sourceValue);
            case I2D, L2D, F2D -> AdvInsnBuilder.toDouble(sourceValue);
            case L2I, F2I, D2I -> AdvInsnBuilder.toInt(sourceValue);
            case I2B -> AdvInsnBuilder.shiftRight(
                    AdvInsnBuilder.shiftLeft(sourceValue, AdvInsnBuilder.constant(24)),
                    AdvInsnBuilder.constant(24));
            case I2C -> AdvInsnBuilder.bitAnd(sourceValue, AdvInsnBuilder.constant(0xffff));
            case I2S -> AdvInsnBuilder.shiftRight(
                    AdvInsnBuilder.shiftLeft(sourceValue, AdvInsnBuilder.constant(16)),
                    AdvInsnBuilder.constant(16));
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
        registerWrite(
                ib,
                context,
                target.box(target == NumericType.INT ? AdvInsnBuilder.cast(converted, "I") : converted));
    }

    private void emitRegisterCompare(AdvInsnBuilder ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        Local left = ib.var("registerCompareLeft" + opcode, type.descriptor());
        Local right = ib.var("registerCompareRight" + opcode, type.descriptor());
        Local result = ib.var("registerCompareResult" + opcode, "I");
        ib.set(left, type.unbox(registerRead(context, context.sourceA)));
        ib.set(right, type.unbox(registerRead(context, context.sourceB)));
        if (opcode == Opcs.LCMP)
        {
            emitOrderedRegisterCompare(ib, left, right, result);
        }
        else
        {
            boolean greaterOnNaN = opcode == Opcs.FCMPG || opcode == Opcs.DCMPG;
            Expr leftNaN = AdvInsnBuilder.callStatic(
                    type == NumericType.FLOAT ? "java/lang/Float" : "java/lang/Double",
                    "isNaN",
                    "Z",
                    left);
            Expr rightNaN = AdvInsnBuilder.callStatic(
                    type == NumericType.FLOAT ? "java/lang/Float" : "java/lang/Double",
                    "isNaN",
                    "Z",
                    right);
            ib.ifElse(
                    AdvInsnBuilder.or(AdvInsnBuilder.isTrue(leftNaN), AdvInsnBuilder.isTrue(rightNaN)),
                    nan -> nan.set(result, AdvInsnBuilder.constant(greaterOnNaN ? 1 : -1)),
                    ordered -> emitOrderedRegisterCompare(ordered, left, right, result));
        }
        registerWrite(ib, context, NumericType.INT.box(result));
    }

    private void emitOrderedRegisterCompare(AdvInsnBuilder ib, Expr left, Expr right, Local result)
    {
        ib.ifElse(
                AdvInsnBuilder.greaterThan(left, right),
                greater -> greater.set(result, AdvInsnBuilder.constant(1)),
                other -> other.ifElse(
                        AdvInsnBuilder.equal(left, right),
                        equal -> equal.set(result, AdvInsnBuilder.constant(0)),
                        less -> less.set(result, AdvInsnBuilder.constant(-1))));
    }

    private Expr registerRead(RegisterOperationContext context, Expr token)
    {
        return AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.registerRead.name(),
                "java/lang/Object",
                context.program,
                context.frame,
                context.baseStack,
                token);
    }

    private void registerWrite(AdvInsnBuilder ib, RegisterOperationContext context, Expr value)
    {
        registerWrite(ib, context, value, context.width);
    }

    private void registerWrite(AdvInsnBuilder ib, RegisterOperationContext context, Expr value, Expr width)
    {
        ib.directCall(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.registerWrite.name(),
                "V",
                context.program,
                context.frame,
                context.baseStack,
                context.destination,
                AdvInsnBuilder.cast(value, "java/lang/Object"),
                width));
    }

    private record RegisterOperationContext(
            Local program,
            Local frame,
            Local constants,
            Local semantic,
            Local baseStack,
            Local destination,
            Local sourceA,
            Local sourceB,
            Local auxiliary,
            Local width,
            Local instructionIndex,
            Local opcode)
    {
    }

    private void prepareMutableCode(AdvInsnBuilder ib, InterpretContext context)
    {
        ib.ifCondition(
                AdvInsnBuilder.notEqual(
                        context.frameField(frameLayout.mutableProgram),
                        context.program()),
                initialize -> {
                    initialize.set(
                            context.frameField(frameLayout.mutableCode),
                            AdvInsnBuilder.callStatic(
                                    "java/util/Arrays",
                                    "copyOf",
                                    "[I",
                                    context.code(),
                                    AdvInsnBuilder.arrayLength(context.code())));
                    initialize.set(
                            context.frameField(frameLayout.mutableMasks),
                            AdvInsnBuilder.newArray("int", AdvInsnBuilder.arrayLength(context.code())));
                    initialize.set(context.frameField(frameLayout.mutableProgram), context.program());
                });
    }

    private void emitSelfMutation(AdvInsnBuilder ib, InterpretContext context)
    {
        Local mutation = context.intLocal("mutation", InterpretContext.DISPATCH_SELECTOR + 5);
        ib.set(mutation, mixCall(
                context.frameStateKey(),
                context.instructionIndex(),
                context.opcode(),
                AdvInsnBuilder.constant(profile.saltHandler)));
        ib.setArray(
                context.frameField(frameLayout.mutableCode),
                context.instructionIndex(),
                AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.arrayAt(context.frameField(frameLayout.mutableCode), context.instructionIndex()),
                        mutation));
        ib.setArray(
                context.frameField(frameLayout.mutableMasks),
                context.instructionIndex(),
                AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.arrayAt(context.frameField(frameLayout.mutableMasks), context.instructionIndex()),
                        mutation));
    }

    private Expr stepCall(InterpretContext context, Expr structureState)
    {
        List<Expr> arguments = new ArrayList<>();
        arguments.add(context.program());
        arguments.add(context.frame());
        arguments.add(context.code());
        arguments.add(context.constants());
        arguments.add(structureState);
        if (config.vmStructure != VMStructure.SIMPLE_DISPATCH)
        {
            int shape = structurePlan.structure().ordinal();
            for (int bit = 0; bit < 5; bit++)
            {
                arguments.add((shape & 1 << bit) == 0
                        ? AdvInsnBuilder.constant(profile.decodeVariant + bit)
                        : AdvInsnBuilder.constant(
                                ((long) profile.decodeVariant << 32) ^
                                (0x94D049BB133111EBL + bit)));
            }
        }
        return AdvInsnBuilder.callStatic(
                className(),
                vmLayout.interpretStep.name(),
                "I",
                arguments.toArray(Expr[]::new));
    }

    private Expr graphNode(InterpretContext context)
    {
        return mixCall(
                context.frameProgramCounter(),
                context.frameField(frameLayout.blockIndex),
                context.frameStateKey(),
                AdvInsnBuilder.constant(profile.saltBlock));
    }

    private Expr fsmState(InterpretContext context, Expr previousState)
    {
        return AdvInsnBuilder.bitXor(
                mixCall(
                        context.frameProgramCounter(),
                        context.frameField(frameLayout.blockIndex),
                        previousState,
                        AdvInsnBuilder.constant(profile.saltState)),
                AdvInsnBuilder.constant(profile.saltState));
    }

    private Expr eventToken(InterpretContext context)
    {
        return mixCall(
                context.frameStateKey(),
                context.frameProgramCounter(),
                context.frameField(frameLayout.blockIndex),
                AdvInsnBuilder.constant(profile.saltHandler));
    }

    private String schedulerDescriptor(boolean depth)
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;" +
                (depth ? "I" : "") +
                ")I";
    }

    private String coroutineDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;[I)I";
    }

    private MethodNode genInstructionIndexMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.instructionIndex.name(), vmLayout.instructionIndex.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local pc = ib.getLocal("pc", "I", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local block = ib.getLocal("block", "I", 4);
        Local blockCount = ib.getLocal("blockCount", "I", 5);

        ib.set(block, AdvInsnBuilder.field(frame, frameLayout.blockIndex));
        ib.set(index, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndexInBlock.name(),
                "I",
                program,
                block,
                pc));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(index, AdvInsnBuilder.constant(-1)),
                found -> found.returnValue(index));

        ib.set(blockCount, AdvInsnBuilder.divide(
                AdvInsnBuilder.arrayLength(callProgramArray(program, programLayout.blockStream.name())),
                AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_SIZE)));
        ib.forLoop(
                b -> b.set(block, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(block, blockCount),
                b -> b.increment(block, 1),
                b -> {
                    b.set(index, AdvInsnBuilder.callStatic(
                            vmLayout.owner,
                            vmLayout.instructionIndexInBlock.name(),
                            "I",
                            program,
                            block,
                            pc));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(index, AdvInsnBuilder.constant(-1)),
                            found -> {
                                found.set(AdvInsnBuilder.field(frame, frameLayout.blockIndex), block);
                                found.set(AdvInsnBuilder.field(frame, frameLayout.stateKey), AdvInsnBuilder.callStatic(
                                        vmLayout.owner,
                                        vmLayout.stateKey.name(),
                                        "I",
                                        program,
                                        index));
                                found.returnValue(index);
                            });
                });
        ib.returnValue(AdvInsnBuilder.constant(-1));
        return method;
    }

    private MethodNode genDecodeOpcodeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.decodeOpcode.name(), vmLayout.decodeOpcode.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local index = ib.getLocal("instructionIndex", "I", 2);
        Local key = ib.getLocal("methodKey", "I", 3);
        Local stateKey = ib.getLocal("stateKey", "I", 4);
        Local virtualPc = ib.getLocal("virtualPc", "I", 5);
        Local virtualOpcode = ib.getLocal("virtualOpcode", "I", 6);
        Local mappedOpcode = ib.getLocal("mappedOpcode", "I", 7);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvInsnBuilder.field(frame, frameLayout.stateKey));
        ib.set(virtualPc, layoutValue(program, index, ProtectedVMMethod.LAYOUT_PC, stateKey));
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            ib.set(virtualOpcode, AdvInsnBuilder.bitXor(
                    AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.mutableCode), index),
                    AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.mutableMasks), index)));
        }
        else
        {
            ib.set(virtualOpcode, AdvInsnBuilder.arrayAt(callProgramArray(program, programLayout.opcodeStream.name()), index));
        }
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                b -> b.set(virtualOpcode, AdvInsnBuilder.bitXor(
                        virtualOpcode,
                        mixCall(
                                AdvInsnBuilder.bitXor(key, stateKey),
                                virtualPc,
                                index,
                                AdvInsnBuilder.constant(profile.saltOpcode)))));
        ib.set(mappedOpcode, AdvInsnBuilder.arrayAt(callProgramArray(program, programLayout.opcodeMap.name()), virtualOpcode));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                b -> b.set(mappedOpcode, AdvInsnBuilder.bitXor(
                        mappedOpcode,
                        mixCall(
                                key,
                                virtualOpcode,
                                AdvInsnBuilder.constant(profile.saltOpcodeMap),
                                AdvInsnBuilder.constant(0)))));
        ib.returnValue(mappedOpcode);
        return method;
    }

    private MethodNode genDecodeNextPcMethod()
    {
        return genDecodeLayoutFieldMethod(vmLayout.decodeNextPc.name(), vmLayout.decodeNextPc.descriptor(), ProtectedVMMethod.LAYOUT_NEXT_PC);
    }

    private MethodNode genDecodeOriginalPcMethod()
    {
        return genDecodeLayoutFieldMethod(vmLayout.decodeOriginalPc.name(), vmLayout.decodeOriginalPc.descriptor(), ProtectedVMMethod.LAYOUT_ORIGINAL_PC);
    }

    private MethodNode genDecodeLayoutFieldMethod(String name, String descriptor, int field)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local index = ib.getLocal("instructionIndex", "I", 2);
        ib.returnValue(layoutValue(
                program,
                index,
                field,
                AdvInsnBuilder.field(frame, frameLayout.stateKey)));
        return method;
    }

    private MethodNode genDecodeOperandMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.decodeOperand.name(), vmLayout.decodeOperand.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 2);
        Local operandIndex = ib.getLocal("operandIndex", "I", 3);
        Local opcode = ib.getLocal("opcode", "I", 4);
        Local key = ib.getLocal("methodKey", "I", 5);
        Local stateKey = ib.getLocal("stateKey", "I", 6);
        Local virtualPc = ib.getLocal("virtualPc", "I", 7);
        Local operandStart = ib.getLocal("operandStart", "I", 8);
        Local operandCount = ib.getLocal("operandCount", "I", 9);
        Local operandPosition = ib.getLocal("operandPosition", "I", 10);
        Local constantMask = ib.getLocal("constantMask", "I", 11);
        Local value = ib.getLocal("value", "I", 12);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvInsnBuilder.field(frame, frameLayout.stateKey));
        ib.set(virtualPc, layoutValue(program, instructionIndex, ProtectedVMMethod.LAYOUT_PC, stateKey));
        ib.set(operandStart, layoutValue(program, instructionIndex, ProtectedVMMethod.LAYOUT_OPERAND_START, stateKey));
        ib.set(operandCount, layoutValue(program, instructionIndex, ProtectedVMMethod.LAYOUT_OPERAND_COUNT, stateKey));
        ib.ifCondition(
                AdvInsnBuilder.greaterOrEqual(operandIndex, operandCount),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(AdvInsnBuilder.constant("Operand out of range "), operandIndex))));
        ib.set(operandPosition, AdvInsnBuilder.plus(operandStart, operandIndex));
        ib.set(value, AdvInsnBuilder.arrayAt(callProgramArray(program, programLayout.operandStream.name()), operandPosition));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_ENCRYPT_OPERANDS)),
                b -> b.set(value, AdvInsnBuilder.bitXor(
                        value,
                        mixCall(
                                AdvInsnBuilder.bitXor(AdvInsnBuilder.bitXor(key, stateKey), opcode),
                                virtualPc,
                                operandIndex,
                                AdvInsnBuilder.bitXor(AdvInsnBuilder.constant(profile.saltOperand), operandPosition)))));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_BIND_CONSTANTS)),
                b -> {
                    b.set(constantMask, layoutValue(
                            program,
                            instructionIndex,
                            ProtectedVMMethod.LAYOUT_CONSTANT_MASK,
                            stateKey));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(
                                    AdvInsnBuilder.bitAnd(constantMask, AdvInsnBuilder.shiftLeft(AdvInsnBuilder.constant(1), operandIndex)),
                                    AdvInsnBuilder.constant(0)),
                            constant -> constant.set(value, AdvInsnBuilder.bitXor(
                                    value,
                                    mixCall(
                                            AdvInsnBuilder.bitXor(AdvInsnBuilder.bitXor(key, stateKey), opcode),
                                            virtualPc,
                                            operandIndex,
                                            AdvInsnBuilder.constant(profile.saltConstant)))));
                });
        ib.returnValue(value);
        return method;
    }

    private void emitDecodeOpcodeInline(AdvInsnBuilder ib, InterpretContext context, Local target)
    {
        Local key = context.intLocal("opcodeMethodKey", 100);
        Local stateKey = context.intLocal("opcodeStateKey", 101);
        Local virtualPc = context.intLocal("opcodeVirtualPc", 102);
        Local virtualOpcode = context.intLocal("encodedOpcode", 103);
        Local mappedOpcode = context.intLocal("mappedOpcode", 104);

        ib.set(key, callProgramInt(context.program(), programLayout.methodKey.name()));
        ib.set(stateKey, context.frameStateKey());
        emitLayoutValueInline(
                ib,
                context,
                virtualPc,
                ProtectedVMMethod.LAYOUT_PC,
                105);
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            ib.set(virtualOpcode, AdvInsnBuilder.bitXor(
                    AdvInsnBuilder.arrayAt(
                            AdvInsnBuilder.field(context.frame(), frameLayout.mutableCode),
                            context.instructionIndex()),
                    AdvInsnBuilder.arrayAt(
                            AdvInsnBuilder.field(context.frame(), frameLayout.mutableMasks),
                            context.instructionIndex())));
        }
        else
        {
            ib.set(virtualOpcode, AdvInsnBuilder.arrayAt(
                    callProgramArray(context.program(), programLayout.opcodeStream.name()),
                    context.instructionIndex()));
        }
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                decode -> decode.set(virtualOpcode, structureXorDecode(
                        virtualOpcode,
                        mixCall(
                                AdvInsnBuilder.bitXor(key, stateKey),
                                virtualPc,
                                context.instructionIndex(),
                                AdvInsnBuilder.constant(profile.saltOpcode)),
                        profile.saltOpcode)));
        ib.set(mappedOpcode, AdvInsnBuilder.arrayAt(
                callProgramArray(context.program(), programLayout.opcodeMap.name()),
                virtualOpcode));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                decode -> decode.set(mappedOpcode, structureXorDecode(
                        mappedOpcode,
                        mixCall(
                                key,
                                virtualOpcode,
                                AdvInsnBuilder.constant(profile.saltOpcodeMap),
                                AdvInsnBuilder.constant(0)),
                        profile.saltOpcodeMap)));
        ib.set(target, mappedOpcode);
    }

    private void emitDecodeOperandInline(
            AdvInsnBuilder ib,
            InterpretContext context,
            Local target)
    {
        emitDecodeOperandInline(ib, context, target, 0);
    }

    private void emitDecodeOperandInline(
            AdvInsnBuilder ib,
            InterpretContext context,
            Local target,
            int decoderSite)
    {
        Local key = context.intLocal("operandMethodKey", 120);
        Local stateKey = context.intLocal("operandStateKey", 121);
        Local virtualPc = context.intLocal("operandVirtualPc", 122);
        Local operandStart = context.intLocal("operandStart", 123);
        Local operandCount = context.intLocal("operandCount", 124);
        Local operandPosition = context.intLocal("operandPosition", 125);
        Local constantMask = context.intLocal("operandConstantMask", 126);
        Local value = context.intLocal("encodedOperand", 127);

        ib.set(key, callProgramInt(context.program(), programLayout.methodKey.name()));
        ib.set(stateKey, context.frameStateKey());
        emitLayoutValueInline(
                ib,
                context,
                virtualPc,
                ProtectedVMMethod.LAYOUT_PC,
                128);
        emitLayoutValueInline(
                ib,
                context,
                operandStart,
                ProtectedVMMethod.LAYOUT_OPERAND_START,
                132);
        emitLayoutValueInline(
                ib,
                context,
                operandCount,
                ProtectedVMMethod.LAYOUT_OPERAND_COUNT,
                136);
        ib.ifCondition(
                AdvInsnBuilder.greaterOrEqual(context.operandIndex(), operandCount),
                outOfRange -> outOfRange.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(
                                AdvInsnBuilder.constant("Operand out of range "),
                                context.operandIndex()))));
        ib.set(operandPosition, AdvInsnBuilder.plus(operandStart, context.operandIndex()));
        ib.set(value, AdvInsnBuilder.arrayAt(
                callProgramArray(context.program(), programLayout.operandStream.name()),
                operandPosition));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_ENCRYPT_OPERANDS)),
                decode -> decode.set(value, structureXorDecode(
                        value,
                        mixCall(
                                AdvInsnBuilder.bitXor(
                                        AdvInsnBuilder.bitXor(key, stateKey),
                                        context.opcode()),
                                virtualPc,
                                context.operandIndex(),
                                AdvInsnBuilder.bitXor(
                                        AdvInsnBuilder.constant(profile.saltOperand),
                                        operandPosition)),
                        profile.saltOperand ^ decoderSite)));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_BIND_CONSTANTS)),
                bound -> {
                    emitLayoutValueInline(
                            bound,
                            context,
                            constantMask,
                            ProtectedVMMethod.LAYOUT_CONSTANT_MASK,
                            140);
                    bound.ifCondition(
                            AdvInsnBuilder.notEqual(
                                    AdvInsnBuilder.bitAnd(
                                            constantMask,
                                            AdvInsnBuilder.shiftLeft(
                                                    AdvInsnBuilder.constant(1),
                                                    context.operandIndex())),
                                    AdvInsnBuilder.constant(0)),
                            decode -> decode.set(value, structureXorDecode(
                                    value,
                                    mixCall(
                                            AdvInsnBuilder.bitXor(
                                                    AdvInsnBuilder.bitXor(key, stateKey),
                                                    context.opcode()),
                                            virtualPc,
                                            context.operandIndex(),
                                            AdvInsnBuilder.constant(profile.saltConstant)),
                                    profile.saltConstant ^ decoderSite)));
                });
        ib.set(target, value);
    }

    private void emitLayoutValueInline(
            AdvInsnBuilder ib,
            InterpretContext context,
            Local target,
            int field,
            int scratchBase)
    {
        Local key = context.intLocal("layoutKey" + scratchBase, scratchBase);
        Local raw = context.intLocal("layoutRaw" + scratchBase, scratchBase + 1);
        ib.set(key, callProgramInt(context.program(), programLayout.methodKey.name()));
        ib.set(raw, AdvInsnBuilder.arrayAt(
                callProgramArray(context.program(), programLayout.layoutStream.name()),
                AdvInsnBuilder.plus(
                        AdvInsnBuilder.multiply(
                                context.instructionIndex(),
                                AdvInsnBuilder.constant(ProtectedVMMethod.RECORD_SIZE)),
                        AdvInsnBuilder.constant(profile.layoutSlot(field)))));
        ib.set(target, raw);
        ib.ifCondition(
                AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                decode -> decode.set(target, structureXorDecode(
                        raw,
                        mixCall(
                                AdvInsnBuilder.bitXor(key, context.frameStateKey()),
                                context.instructionIndex(),
                                AdvInsnBuilder.constant(profile.layoutSlot(field)),
                                AdvInsnBuilder.constant(profile.saltLayout)),
                        profile.saltLayout ^ profile.layoutSlot(field))));
    }

    private Expr structureXorDecode(Expr value, Expr mask, int siteSalt)
    {
        return switch ((profile.decodeVariant ^ structurePlan.structure().ordinal() ^ siteSalt) & 3)
        {
            case 0 -> AdvInsnBuilder.bitXor(value, mask);
            case 1 -> AdvInsnBuilder.bitXor(
                    AdvInsnBuilder.bitXor(value, AdvInsnBuilder.constant(siteSalt)),
                    AdvInsnBuilder.bitXor(mask, AdvInsnBuilder.constant(siteSalt)));
            case 2 -> AdvInsnBuilder.bitNot(AdvInsnBuilder.bitXor(
                    AdvInsnBuilder.bitNot(value),
                    mask));
            default -> AdvInsnBuilder.bitNot(AdvInsnBuilder.bitXor(
                    value,
                    AdvInsnBuilder.bitNot(mask)));
        };
    }

    private MethodNode genLayoutValueMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.layoutValue.name(), vmLayout.layoutValue.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 1);
        Local field = ib.getLocal("field", "I", 2);
        Local stateKey = ib.getLocal("stateKey", "I", 3);
        Local key = ib.getLocal("methodKey", "I", 4);
        Local raw = ib.getLocal("raw", "I", 5);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.ifCondition(
                AdvInsnBuilder.equal(
                        field,
                        AdvInsnBuilder.constant(profile.layoutSlot(ProtectedVMMethod.LAYOUT_STATE_KEY))),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.stateKey.name(),
                        "I",
                        program,
                        instructionIndex)));
        ib.set(raw, AdvInsnBuilder.arrayAt(
                callProgramArray(program, programLayout.layoutStream.name()),
                AdvInsnBuilder.plus(
                        AdvInsnBuilder.multiply(instructionIndex, AdvInsnBuilder.constant(ProtectedVMMethod.RECORD_SIZE)),
                        field)));
        ib.ifCondition(AdvInsnBuilder.equal(key, AdvInsnBuilder.constant(0)), b -> b.returnValue(raw));
        ib.returnValue(AdvInsnBuilder.bitXor(
                raw,
                mixCall(
                        AdvInsnBuilder.bitXor(key, stateKey),
                        instructionIndex,
                        field,
                        AdvInsnBuilder.constant(profile.saltLayout))));
        return method;
    }

    private MethodNode genBlockValueMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.blockValue.name(), vmLayout.blockValue.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local blockIndex = ib.getLocal("blockIndex", "I", 1);
        Local field = ib.getLocal("field", "I", 2);
        Local key = ib.getLocal("methodKey", "I", 3);
        Local raw = ib.getLocal("raw", "I", 4);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(raw, AdvInsnBuilder.arrayAt(
                callProgramArray(program, programLayout.blockStream.name()),
                AdvInsnBuilder.plus(
                        AdvInsnBuilder.multiply(blockIndex, AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_SIZE)),
                        field)));
        ib.ifCondition(AdvInsnBuilder.equal(key, AdvInsnBuilder.constant(0)), b -> b.returnValue(raw));
        ib.returnValue(AdvInsnBuilder.bitXor(
                raw,
                mixCall(
                        key,
                        blockIndex,
                        field,
                        AdvInsnBuilder.constant(profile.saltBlock))));
        return method;
    }

    private MethodNode genStateKeyMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.stateKey.name(), vmLayout.stateKey.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 1);
        Local key = ib.getLocal("methodKey", "I", 2);
        Local raw = ib.getLocal("raw", "I", 3);
        Local blockCount = ib.getLocal("blockCount", "I", 4);
        Local blockIndex = ib.getLocal("blockIndex", "I", 5);
        Local firstSlot = ib.getLocal("firstSlot", "I", 6);
        Local slotCount = ib.getLocal("slotCount", "I", 7);
        Local slot = ib.getLocal("stateSlot", "I", 8);
        Local current = ib.getLocal("currentStateKey", "I", 9);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(raw, stateKeyCipherAt(program, instructionIndex));
        ib.ifCondition(
                AdvInsnBuilder.equal(key, AdvInsnBuilder.constant(0)),
                plain -> plain.returnValue(raw));
        ib.set(blockCount, AdvInsnBuilder.divide(
                AdvInsnBuilder.arrayLength(callProgramArray(program, programLayout.blockStream.name())),
                AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_SIZE)));
        ib.forLoop(
                loop -> loop.set(blockIndex, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(blockIndex, blockCount),
                loop -> loop.increment(blockIndex, 1),
                loop -> {
                    loop.set(firstSlot, blockValue(
                            program,
                            blockIndex,
                            AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_START_SLOT)));
                    loop.set(slotCount, blockValue(
                            program,
                            blockIndex,
                            AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_SLOT_COUNT)));
                    loop.ifCondition(
                            AdvInsnBuilder.and(
                                    AdvInsnBuilder.greaterOrEqual(instructionIndex, firstSlot),
                                    AdvInsnBuilder.lessThan(
                                            instructionIndex,
                                            AdvInsnBuilder.plus(firstSlot, slotCount))),
                            containing -> {
                                containing.set(current, AdvInsnBuilder.constant(0));
                                containing.forLoop(
                                        chain -> chain.set(slot, firstSlot),
                                        AdvInsnBuilder.lessOrEqual(slot, instructionIndex),
                                        chain -> chain.increment(slot, 1),
                                        chain -> {
                                            chain.set(raw, stateKeyCipherAt(program, slot));
                                            chain.ifElse(
                                                    AdvInsnBuilder.equal(slot, firstSlot),
                                                    entry -> entry.set(current, AdvInsnBuilder.bitXor(
                                                            raw,
                                                            mixCall(
                                                                    key,
                                                                    slot,
                                                                    AdvInsnBuilder.constant(profile.saltState),
                                                                    AdvInsnBuilder.constant(0)))),
                                                    linked -> linked.set(current, AdvInsnBuilder.bitXor(
                                                            raw,
                                                            mixCall(
                                                                    AdvInsnBuilder.bitXor(key, current),
                                                                    slot,
                                                                    blockIndex,
                                                                    AdvInsnBuilder.constant(profile.saltState)))));
                                        });
                                containing.returnValue(current);
                            });
                });
        ib.returnValue(AdvInsnBuilder.constant(0));
        return method;
    }

    private Expr stateKeyCipherAt(Expr program, Expr instructionIndex)
    {
        return AdvInsnBuilder.arrayAt(
                callProgramArray(program, programLayout.layoutStream.name()),
                AdvInsnBuilder.plus(
                        AdvInsnBuilder.multiply(
                                instructionIndex,
                                AdvInsnBuilder.constant(ProtectedVMMethod.RECORD_SIZE)),
                        AdvInsnBuilder.constant(
                                profile.layoutSlot(ProtectedVMMethod.LAYOUT_STATE_KEY))));
    }

    private MethodNode genInstructionIndexInBlockMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.instructionIndexInBlock.name(), vmLayout.instructionIndexInBlock.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local blockIndex = ib.getLocal("blockIndex", "I", 1);
        Local pc = ib.getLocal("pc", "I", 2);
        Local blockCount = ib.getLocal("blockCount", "I", 3);
        Local firstSlot = ib.getLocal("firstSlot", "I", 4);
        Local slotCount = ib.getLocal("slotCount", "I", 5);
        Local offset = ib.getLocal("offset", "I", 6);
        Local slot = ib.getLocal("slot", "I", 7);
        Local stateKey = ib.getLocal("stateKey", "I", 8);
        Local key = ib.getLocal("methodKey", "I", 9);
        Local rawStateKey = ib.getLocal("rawStateKey", "I", 10);

        ib.set(blockCount, AdvInsnBuilder.divide(
                AdvInsnBuilder.arrayLength(callProgramArray(program, programLayout.blockStream.name())),
                AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_SIZE)));
        ib.ifCondition(
                AdvInsnBuilder.or(
                        AdvInsnBuilder.lessThan(blockIndex, AdvInsnBuilder.constant(0)),
                        AdvInsnBuilder.greaterOrEqual(blockIndex, blockCount)),
                b -> b.returnValue(AdvInsnBuilder.constant(-1)));
        ib.set(firstSlot, blockValue(program, blockIndex, AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_START_SLOT)));
        ib.set(slotCount, blockValue(program, blockIndex, AdvInsnBuilder.constant(ProtectedVMMethod.BLOCK_SLOT_COUNT)));
        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvInsnBuilder.constant(0));
        ib.forLoop(
                b -> b.set(offset, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(offset, slotCount),
                b -> b.increment(offset, 1),
                b -> {
                    b.set(slot, AdvInsnBuilder.plus(firstSlot, offset));
                    b.set(rawStateKey, stateKeyCipherAt(program, slot));
                    b.ifElse(
                            AdvInsnBuilder.equal(key, AdvInsnBuilder.constant(0)),
                            plain -> plain.set(stateKey, rawStateKey),
                            protectedState -> protectedState.ifElse(
                                    AdvInsnBuilder.equal(offset, AdvInsnBuilder.constant(0)),
                                    entry -> entry.set(stateKey, AdvInsnBuilder.bitXor(
                                            rawStateKey,
                                            mixCall(
                                                    key,
                                                    slot,
                                                    AdvInsnBuilder.constant(profile.saltState),
                                                    AdvInsnBuilder.constant(0)))),
                                    linked -> linked.set(stateKey, AdvInsnBuilder.bitXor(
                                            rawStateKey,
                                            mixCall(
                                                    AdvInsnBuilder.bitXor(key, stateKey),
                                                    slot,
                                                    blockIndex,
                                                    AdvInsnBuilder.constant(profile.saltState))))));
                    b.ifCondition(
                            AdvInsnBuilder.equal(
                                    layoutValue(program, slot, ProtectedVMMethod.LAYOUT_PC, stateKey),
                                    pc),
                            found -> found.returnValue(slot));
                    b.ifCondition(
                            AdvInsnBuilder.equal(
                                    layoutValue(program, slot, ProtectedVMMethod.LAYOUT_ORIGINAL_PC, stateKey),
                                    pc),
                            found -> found.returnValue(slot));
                });
        ib.returnValue(AdvInsnBuilder.constant(-1));
        return method;
    }

    private MethodNode genSyncStateMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.syncState.name(), vmLayout.syncState.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local pc = ib.getLocal("pc", "I", 2);
        Local index = ib.getLocal("instructionIndex", "I", 3);
        Local stateKey = ib.getLocal("stateKey", "I", 4);
        Local blockIndex = ib.getLocal("blockIndex", "I", 5);

        ib.set(index, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndex.name(),
                "I",
                program,
                frame,
                pc));
        ib.ifCondition(
                AdvInsnBuilder.equal(index, AdvInsnBuilder.constant(-1)),
                b -> {
                    b.set(AdvInsnBuilder.field(frame, frameLayout.stateKey), AdvInsnBuilder.constant(0));
                    b.set(AdvInsnBuilder.field(frame, frameLayout.blockIndex), AdvInsnBuilder.constant(-1));
                    b.returnVoid();
                });
        ib.set(stateKey, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.stateKey.name(),
                "I",
                program,
                index));
        ib.set(stateKey, AdvInsnBuilder.bitXor(stateKey, AdvInsnBuilder.field(frame, frameLayout.integrityKey)));
        ib.set(blockIndex, layoutValue(program, index, ProtectedVMMethod.LAYOUT_BLOCK_INDEX, stateKey));
        ib.set(AdvInsnBuilder.field(frame, frameLayout.stateKey), stateKey);
        ib.set(AdvInsnBuilder.field(frame, frameLayout.blockIndex), blockIndex);
        ib.returnVoid();
        return method;
    }

    private MethodNode genMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.mix.name(), vmLayout.mix.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local key = ib.getLocal("key", "I", 0);
        Local a = ib.getLocal("a", "I", 1);
        Local b = ib.getLocal("b", "I", 2);
        Local c = ib.getLocal("c", "I", 3);
        Local x = ib.getLocal("x", "I", 4);
        ib.set(x, AdvInsnBuilder.bitXor(key, AdvInsnBuilder.constant(profile.mixSeed)));
        mixRound(ib, x, a, profile.mixRoundA);
        mixRound(ib, x, b, profile.mixRoundB);
        mixRound(ib, x, c, profile.mixRoundC);
        ib.set(x, AdvInsnBuilder.bitXor(x, AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(16))));
        ib.set(x, AdvInsnBuilder.multiply(x, AdvInsnBuilder.constant(profile.mixMulA)));
        ib.set(x, AdvInsnBuilder.bitXor(x, AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(15))));
        ib.set(x, AdvInsnBuilder.multiply(x, AdvInsnBuilder.constant(profile.mixMulB)));
        ib.set(x, AdvInsnBuilder.bitXor(x, AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(16))));
        ib.returnValue(x);
        return method;
    }

    private MethodNode genDispatchKeyMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.dispatchKey.name(), vmLayout.dispatchKey.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local opcode = ib.getLocal("opcode", "I", 0);
        ib.returnValue(dispatchKeyExpr(opcode));
        return method;
    }

    private void generateDispatch(AdvInsnBuilder ib, LabelNode afterDispatch, LabelNode unknownOpcode)
    {
        Set<Opcs> opcodeSet = EnumSet.noneOf(Opcs.class);
        switch (config.interpretMode)
        {
            case SAVE_ALL_INSTRUCTION -> opcodeSet.addAll(branches.keySet());
            case SAVE_ONLY_REQUIRED_INSTRUCTION ->
            {
                for(CodePoolGenerator codePoolGenerator : codePoolGenerators)
                {
                    opcodeSet.addAll(codePoolGenerator.getUsedOpcodes());
                }
            }
        }
        if (structurePlan.schedulerKind() != VMStructurePlan.SchedulerKind.REGISTER)
        {
            opcodeSet.remove(Opcs.REGISTER_OP);
        }
        if (structurePlan.schedulerKind() != VMStructurePlan.SchedulerKind.DATA_FLOW)
        {
            opcodeSet.remove(Opcs.DATA_FLOW_REGION);
        }
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.REGISTER)
        {
            opcodeSet.add(Opcs.REGISTER_OP);
        }
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.DATA_FLOW)
        {
            opcodeSet.add(Opcs.DATA_FLOW_REGION);
        }
        if (!superInstructions.recipes().isEmpty())
        {
            opcodeSet.add(Opcs.SUPER_INSTRUCTION);
            superInstructionChunks = createSuperInstructionChunks(superInstructions.recipes());
            for (SuperInstructionChunk chunk : superInstructionChunks)
            {
                classNode.methods.add(chunk.method);
            }
        }

        List<Opcs> opcodes = new ArrayList<>(opcodeSet);
        opcodes.sort((left, right) -> Integer.compare(
                dispatchOpcode(left),
                dispatchOpcode(right)
        ));

        List<InterpretChunk> chunks = createInterpretChunks(opcodes);
        Map<Opcs, InterpretChunkSlot> slotByOpcode = new EnumMap<>(Opcs.class);
        for (InterpretChunk chunk : chunks)
        {
            classNode.methods.add(chunk.method);
            for (int opcodeIndex = 0; opcodeIndex < chunk.opcodes.size(); opcodeIndex++)
            {
                slotByOpcode.put(chunk.opcodes.get(opcodeIndex), new InterpretChunkSlot(chunk.index, opcodeIndex));
            }
        }

        List<DispatchEntry> entries = createDispatchEntries(opcodes, slotByOpcode);
        if (structureGenerator instanceof VMDispatchGenerator dispatchGenerator)
        {
            List<VMDispatchTarget> targets = entries.stream()
                    .map(entry -> new VMDispatchTarget(
                            entry.key,
                            entry.primaryKey,
                            entry.opcode,
                            entry.slot.chunkIndex,
                            entry.slot.opcodeIndex,
                            interpretChunkName(entry.slot.chunkIndex),
                            interpretChunkDescriptor()))
                    .toList();
            dispatchGenerator.emitDispatch(new VMDispatchGenerationContext(
                    structureGeneration,
                    ib,
                    interpretContext(afterDispatch),
                    targets,
                    afterDispatch,
                    unknownOpcode,
                    (instructions, runtime, target, instructionIndex) -> emitChunkCall(
                            instructions,
                            runtime,
                            new InterpretChunkSlot(target.handlerGroup(), target.handlerIndex()),
                            instructionIndex),
                    this::setDispatchSelector,
                    dispatchDescriptor()));
            return;
        }
        throw new IllegalStateException(
                "VM structure has no dedicated dispatch generator: " + structureGenerator.structure());
    }

    private List<DispatchEntry> createDispatchEntries(
            List<Opcs> opcodes,
            Map<Opcs, InterpretChunkSlot> slotByOpcode)
    {
        List<DispatchEntry> entries = new ArrayList<>();
        for (Opcs opcode : opcodes)
        {
            InterpretChunkSlot slot = slotByOpcode.get(opcode);
            if (slot == null)
            {
                throw new IllegalStateException("Opcode has no interpret chunk slot: " + opcode);
            }
            int primaryKey = dispatchOpcode(opcode);
            entries.add(new DispatchEntry(primaryKey, primaryKey, opcode, slot));
            int alternateKey = dispatchKey(primaryKey);
            if (alternateKey != primaryKey)
            {
                entries.add(new DispatchEntry(alternateKey, primaryKey, opcode, slot));
            }
        }
        return List.copyOf(entries);
    }

    private void setDispatchSelector(AdvInsnBuilder ib, InterpretContext context, Local selector)
    {
        ib.set(selector, context.opcode());
        ib.ifCondition(
                featureEnabled(context.program(), ProtectedVMMethod.FEATURE_OBFUSCATE_DISPATCH),
                b -> b.set(selector, AdvInsnBuilder.callStatic(
                        className(),
                        vmLayout.dispatchKey.name(),
                        "I",
                        context.opcode())));
    }

    private void emitChunkCall(
            AdvInsnBuilder ib,
            InterpretContext context,
            InterpretChunkSlot slot,
            Expr instructionIndex)
    {
        ib.directCall(AdvInsnBuilder.callStatic(
                className(),
                interpretChunkName(slot.chunkIndex),
                "V",
                semanticHandlerArguments(
                        context,
                        AdvInsnBuilder.constant(slot.opcodeIndex),
                        instructionIndex)));
    }

    private InterpretContext interpretContext(LabelNode loopStart)
    {
        return new InterpretContext(
                className(),
                frameLayout,
                programLayout,
                vmLayout,
                loopStart,
                config.vmStructure == VMStructure.SIMPLE_DISPATCH ? null : this::emitDecodeOperandInline);
    }

    private int dispatchOpcode(Opcs opcode)
    {
        int mutated = opcMutator.toMutated(opcode);
        return structurePlan.usesDirectTokens() ? profile.directHandlerToken(mutated) : mutated;
    }

    private String structureMethodName(String baseName, String descriptor)
    {
        return namer.method(className(), baseName, descriptor);
    }

    private String structureClassName(String baseName)
    {
        return namer.className(
                classPackage(className()),
                classSimpleName(className()) + '$' + baseName);
    }

    private static String classPackage(String className)
    {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash);
    }

    private static String classSimpleName(String className)
    {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? className : className.substring(slash + 1);
    }

    private String dispatchDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;II)I";
    }

    private List<InterpretChunk> createInterpretChunks(List<Opcs> opcodes)
    {
        if (config.vmStructure != VMStructure.SIMPLE_DISPATCH)
        {
            List<InterpretChunk> handlers = new ArrayList<>(opcodes.size());
            for (int index = 0; index < opcodes.size(); index++)
            {
                handlers.add(newInterpretChunk(index, List.of(opcodes.get(index))));
            }
            return List.copyOf(handlers);
        }
        List<InterpretChunk> chunks = new ArrayList<>();
        List<Opcs> current = new ArrayList<>();
        int chunkIndex = 0;
        for (Opcs opcode : opcodes)
        {
            current.add(opcode);
            if (current.size() <= 1)
            {
                continue;
            }
            if (current.size() > MAX_INTERPRET_CHUNK_OPCODES || tooLargeInterpretChunk(chunkIndex, current))
            {
                current.remove(current.size() - 1);
                chunks.add(newInterpretChunk(chunkIndex++, current));
                current = new ArrayList<>();
                current.add(opcode);
            }
        }
        if (!current.isEmpty())
        {
            chunks.add(newInterpretChunk(chunkIndex, current));
        }
        return List.copyOf(chunks);
    }

    private boolean tooLargeInterpretChunk(int chunkIndex, List<Opcs> opcodes)
    {
        return FileUtils.estimateMaxSize(genInterpretChunkMethod(chunkIndex, opcodes)) > INTERPRET_CHUNK_CODE_SIZE_LIMIT;
    }

    private InterpretChunk newInterpretChunk(int chunkIndex, List<Opcs> opcodes)
    {
        List<Opcs> chunkOpcodes = List.copyOf(opcodes);
        return new InterpretChunk(chunkIndex, chunkOpcodes, genInterpretChunkMethod(chunkIndex, chunkOpcodes));
    }

    private List<SuperInstructionChunk> createSuperInstructionChunks(List<SuperInstructionRegistry.Recipe> recipes)
    {
        List<SuperInstructionChunk> chunks = new ArrayList<>();
        List<SuperInstructionRegistry.Recipe> current = new ArrayList<>();
        int chunkIndex = 0;
        for (SuperInstructionRegistry.Recipe recipe : recipes)
        {
            current.add(recipe);
            if (current.size() <= 1)
            {
                continue;
            }
            if (current.size() > MAX_SUPER_INSTRUCTION_CHUNK_RECIPES ||
                tooLargeSuperInstructionChunk(chunkIndex, current))
            {
                current.remove(current.size() - 1);
                chunks.add(newSuperInstructionChunk(chunkIndex++, current));
                current = new ArrayList<>();
                current.add(recipe);
            }
        }
        if (!current.isEmpty())
        {
            chunks.add(newSuperInstructionChunk(chunkIndex, current));
        }
        return List.copyOf(chunks);
    }

    private boolean tooLargeSuperInstructionChunk(
            int chunkIndex,
            List<SuperInstructionRegistry.Recipe> recipes)
    {
        return FileUtils.estimateMaxSize(genSuperInstructionChunkMethod(chunkIndex, recipes)) >
               SUPER_INSTRUCTION_CHUNK_CODE_SIZE_LIMIT;
    }

    private SuperInstructionChunk newSuperInstructionChunk(
            int chunkIndex,
            List<SuperInstructionRegistry.Recipe> recipes)
    {
        List<SuperInstructionRegistry.Recipe> chunkRecipes = List.copyOf(recipes);
        return new SuperInstructionChunk(
                chunkIndex,
                chunkRecipes,
                genSuperInstructionChunkMethod(chunkIndex, chunkRecipes));
    }

    private MethodNode genInterpretChunkMethod(int chunkIndex, List<Opcs> opcodes)
    {
        MethodNode method = MethodUtils.newMethodNode(
                config.vmStructure == VMStructure.SIMPLE_DISPATCH
                        ? new Acc[]{Acc.PRIVATE, Acc.STATIC}
                        : new Acc[]{Acc.STATIC},
                interpretChunkName(chunkIndex),
                interpretChunkDescriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        if (config.vmStructure != VMStructure.SIMPLE_DISPATCH)
        {
            normalizeSemanticHandlerParameters(ib);
        }
        Local opcodeIndex = ib.getLocal("opcodeIndex", "I", InterpretContext.RIGHT_VALUE);
        Local passedInstructionIndex = ib.getLocal("passedInstructionIndex", "I", 6);
        int decoderSite = profile.decodeVariant ^ chunkIndex * 0x45D9F3B ^
                (opcodes.isEmpty() ? 0 : opcodes.getFirst().ordinal());
        InterpretContext context = new InterpretContext(
                className(),
                frameLayout,
                programLayout,
                vmLayout,
                null,
                config.vmStructure == VMStructure.SIMPLE_DISPATCH
                        ? null
                        : (instructions, runtime, target) -> emitDecodeOperandInline(
                                instructions,
                                runtime,
                                target,
                                decoderSite));
        ib.set(context.instructionIndex(), passedInstructionIndex);
        ib.set(context.operandIndex(), AdvInsnBuilder.constant(0));
        if (opcodes.size() == 1)
        {
            Opcs opcode = opcodes.getFirst();
            if (opcode == Opcs.SUPER_INSTRUCTION)
            {
                generateSuperInstruction(ib, context);
            }
            else
            {
                InterpretBranch branch = branches.get(opcode);
                if (branch == null)
                {
                    throw new IllegalStateException("Missing interpret branch: " + opcode);
                }
                branch.generate(ib, context, opcode);
            }
            ib.returnVoid();
            return method;
        }
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] cases = new java.util.function.Consumer[opcodes.size()];
        for (int i = 0; i < opcodes.size(); i++)
        {
            Opcs opcode = opcodes.get(i);
            if (opcode == Opcs.SUPER_INSTRUCTION)
            {
                cases[i] = b -> generateSuperInstruction(b, context);
            }
            else
            {
                InterpretBranch branch = branches.get(opcode);
                if (branch == null)
                {
                    throw new IllegalStateException("Missing interpret branch: " + opcode);
                }
                cases[i] = b -> branch.generate(b, context, opcode);
            }
        }

        ib.switchTable(
                opcodeIndex,
                0,
                this::generateUnknownOpcode,
                cases);
        ib.returnVoid();
        return method;
    }

    private void generateSuperInstruction(AdvInsnBuilder ib, InterpretContext context)
    {
        Local superId = context.intLocal("superId", InterpretContext.RIGHT_VALUE);
        context.nextOperand(ib, superId);
        List<SuperInstructionRegistry.Recipe> recipes = superInstructions.recipes();
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] cases = new java.util.function.Consumer[recipes.size()];
        for (SuperInstructionChunk chunk : superInstructionChunks)
        {
            for (int recipeIndex = 0; recipeIndex < chunk.recipes.size(); recipeIndex++)
            {
                SuperInstructionRegistry.Recipe recipe = chunk.recipes.get(recipeIndex);
                int localRecipeIndex = recipeIndex;
                cases[recipe.id()] = b -> b.directCall(AdvInsnBuilder.callStatic(
                        className(),
                        superInstructionChunkName(chunk.index),
                        "V",
                        context.program(),
                        context.frame(),
                        context.code(),
                        context.constants(),
                        context.opcode(),
                        AdvInsnBuilder.constant(localRecipeIndex),
                        context.instructionIndex()));
            }
        }
        ib.switchTable(superId, 0, this::generateUnknownOpcode, cases);
    }

    private MethodNode genSuperInstructionChunkMethod(
            int chunkIndex,
            List<SuperInstructionRegistry.Recipe> recipes)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                superInstructionChunkName(chunkIndex),
                superInstructionChunkDescriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local recipeIndex = ib.getLocal("recipeIndex", "I", InterpretContext.RIGHT_VALUE);
        Local passedInstructionIndex = ib.getLocal("passedInstructionIndex", "I", 6);
        int decoderSite = profile.decodeVariant ^ chunkIndex * 0x27D4EB2D;
        InterpretContext context = new InterpretContext(
                className(),
                frameLayout,
                programLayout,
                vmLayout,
                null,
                config.vmStructure == VMStructure.SIMPLE_DISPATCH
                        ? null
                        : (instructions, runtime, target) -> emitDecodeOperandInline(
                                instructions,
                                runtime,
                                target,
                                decoderSite));
        ib.set(context.instructionIndex(), passedInstructionIndex);
        ib.set(context.operandIndex(), AdvInsnBuilder.constant(1));

        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] cases = new java.util.function.Consumer[recipes.size()];
        for (int i = 0; i < recipes.size(); i++)
        {
            SuperInstructionRegistry.Recipe recipe = recipes.get(i);
            cases[i] = b -> {
                for (Opcs opcode : recipe.sequence())
                {
                    InterpretBranch branch = branches.get(opcode);
                    if (branch == null)
                    {
                        throw new IllegalStateException("Missing interpret branch for super instruction body: " + opcode);
                    }
                    branch.generate(b, context, opcode);
                }
            };
        }

        ib.switchTable(recipeIndex, 0, this::generateUnknownOpcode, cases);
        ib.returnVoid();
        return method;
    }

    private String interpretChunkName(int chunkIndex)
    {
        return interpretChunkNames.computeIfAbsent(
                chunkIndex,
                index -> namer.method(
                        className(),
                        config.vmStructure == VMStructure.SIMPLE_DISPATCH
                                ? "interpretChunk$" + index
                                : "handler$" + config.vmStructure.name().toLowerCase(Locale.ROOT) + '$' + index,
                        interpretChunkDescriptor()));
    }

    private String superInstructionChunkName(int chunkIndex)
    {
        return superInstructionChunkNames.computeIfAbsent(
                chunkIndex,
                index -> namer.method(className(), "superInstructionChunk$" + index, superInstructionChunkDescriptor()));
    }

    private String interpretHandlerDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;I)V";
    }

    private String interpretChunkDescriptor()
    {
        if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
        {
            return superInstructionChunkDescriptor();
        }
        int shape = structurePlan.structure().ordinal();
        StringBuilder descriptor = new StringBuilder("(");
        for (SemanticArgument argument : semanticCoreOrder(shape))
        {
            descriptor.append(semanticArgumentDescriptor(argument));
        }
        for (int bit = 0; bit < 5; bit++)
        {
            descriptor.append((shape & 1 << bit) == 0 ? 'I' : 'J');
        }
        return descriptor.append(")V").toString();
    }

    private String superInstructionChunkDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;III)V";
    }

    private Expr[] semanticHandlerArguments(
            InterpretContext context,
            Expr opcodeIndex,
            Expr instructionIndex)
    {
        if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
        {
            return new Expr[]{
                    context.program(),
                    context.frame(),
                    context.code(),
                    context.constants(),
                    context.opcode(),
                    opcodeIndex,
                    instructionIndex
            };
        }
        int shape = structurePlan.structure().ordinal();
        Map<SemanticArgument, Expr> core = new EnumMap<>(SemanticArgument.class);
        core.put(SemanticArgument.PROGRAM, context.program());
        core.put(SemanticArgument.FRAME, context.frame());
        core.put(SemanticArgument.CODE, context.code());
        core.put(SemanticArgument.CONSTANTS, context.constants());
        core.put(SemanticArgument.OPCODE, context.opcode());
        core.put(SemanticArgument.OPCODE_INDEX, opcodeIndex);
        core.put(SemanticArgument.INSTRUCTION_INDEX, instructionIndex);
        List<Expr> arguments = new ArrayList<>();
        for (SemanticArgument argument : semanticCoreOrder(shape))
        {
            arguments.add(core.get(argument));
        }
        for (int bit = 0; bit < 5; bit++)
        {
            arguments.add((shape & 1 << bit) == 0
                    ? AdvInsnBuilder.constant(profile.decodeVariant ^ bit)
                    : AdvInsnBuilder.constant(
                            ((long) profile.decodeVariant << 32) ^
                            (0xD1B54A32D192ED03L + bit)));
        }
        return arguments.toArray(Expr[]::new);
    }

    private void normalizeSemanticHandlerParameters(AdvInsnBuilder ib)
    {
        int shape = structurePlan.structure().ordinal();
        List<SemanticArgument> order = semanticCoreOrder(shape);
        Map<SemanticArgument, Local> copies = new EnumMap<>(SemanticArgument.class);
        for (int slot = 0; slot < order.size(); slot++)
        {
            SemanticArgument argument = order.get(slot);
            Local source = ib.getLocal(
                    "encoded" + argument,
                    semanticArgumentType(argument),
                    slot);
            Local copy = ib.getLocal(
                    "semantic" + argument,
                    semanticArgumentType(argument),
                    160 + argument.ordinal());
            ib.set(copy, source);
            copies.put(argument, copy);
        }
        for (SemanticArgument argument : SemanticArgument.values())
        {
            ib.set(
                    ib.getLocal(
                            "canonical" + argument,
                            semanticArgumentType(argument),
                            argument.ordinal()),
                    copies.get(argument));
        }
    }

    private String semanticArgumentDescriptor(SemanticArgument argument)
    {
        return switch (argument)
        {
            case PROGRAM -> vmProgramGenerator.descriptor();
            case FRAME -> methodFrameGenerator.descriptor();
            case CODE -> "[I";
            case CONSTANTS -> "[Ljava/lang/Object;";
            case OPCODE, OPCODE_INDEX, INSTRUCTION_INDEX -> "I";
        };
    }

    private String semanticArgumentType(SemanticArgument argument)
    {
        return switch (argument)
        {
            case PROGRAM -> programLayout.owner;
            case FRAME -> frameLayout.owner;
            case CODE -> "[I";
            case CONSTANTS -> "[Ljava/lang/Object;";
            case OPCODE, OPCODE_INDEX, INSTRUCTION_INDEX -> "I";
        };
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

    private void generateUnknownOpcode(AdvInsnBuilder ib)
    {
        Local frame = AdvInsnBuilder.local("frame", frameLayout.owner, InterpretContext.FRAME);
        Local opcode = AdvInsnBuilder.local("opcode", "I", InterpretContext.OPCODE);
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/IllegalStateException",
                stringConcat(
                        AdvInsnBuilder.constant("Unknown VM opcode "),
                        AdvInsnBuilder.callStatic("java/lang/Integer", "toHexString", "java/lang/String", opcode),
                        AdvInsnBuilder.constant(" at pc "),
                        AdvInsnBuilder.minus(AdvInsnBuilder.field(frame, frameLayout.programCounter), AdvInsnBuilder.constant(1)))));
    }

    private MethodNode genExecuteMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.VARARGS},
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>(ILjava/lang/Object;[Ljava/lang/Object;)TT;",
                null);
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        ib.returnValue(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                "execute",
                "java/lang/Object",
                codeId,
                receiver,
                arguments,
                AdvInsnBuilder.constant(integrityCapability)));
        return methodNode;
    }

    private MethodNode genExecuteWithIntegrityMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC},
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>(ILjava/lang/Object;[Ljava/lang/Object;I)TT;",
                null);
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local integrityKey = ib.getLocal("integrityKey", "I", 3);
        Local program = ib.getLocal("program", programLayout.owner, 4);
        Local frame = ib.getLocal("frame", frameLayout.owner, 5);
        Local argumentOffset = ib.getLocal("argumentOffset", "I", 6);

        // VMProgram program = resolve(codeId);
        ib.set(program, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.resolve.name(),
                programLayout.owner,
                codeId));

        // MethodFrame frame = new MethodFrame(program.maxLocals(), program.maxStack());
        ib.set(frame, AdvInsnBuilder.newObject(
                frameLayout.owner,
                AdvInsnBuilder.callVirtual(program, programLayout.owner, programLayout.maxLocals.name(), "I"),
                AdvInsnBuilder.callVirtual(program, programLayout.owner, programLayout.maxStack.name(), "I")));
        ib.set(
                AdvInsnBuilder.field(frame, frameLayout.integrityKey),
                AdvInsnBuilder.bitXor(integrityKey, AdvInsnBuilder.constant(integrityCapability)));

        // Instance methods reserve locals[0] for the receiver.
        ib.ifElse(
                AdvInsnBuilder.notNull(receiver),
                b -> {
                    b.setArray(AdvInsnBuilder.field(frame, frameLayout.locals), AdvInsnBuilder.constant(0), receiver);
                    b.set(argumentOffset, AdvInsnBuilder.constant(1));
                },
                b -> b.set(argumentOffset, AdvInsnBuilder.constant(0)));

        // System.arraycopy(arguments, 0, frame.locals, argumentOffset, arguments.length);
        ib.directCall(AdvInsnBuilder.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                AdvInsnBuilder.cast(arguments, "java/lang/Object"),
                AdvInsnBuilder.constant(0),
                AdvInsnBuilder.cast(AdvInsnBuilder.field(frame, frameLayout.locals), "java/lang/Object"),
                argumentOffset,
                AdvInsnBuilder.arrayLength(arguments)));

        ib.directCall(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.syncState.name(),
                "V",
                program,
                frame,
                AdvInsnBuilder.field(frame, frameLayout.programCounter)));

        // interpret(program, frame);
        ib.directCall(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.interpret.name(),
                "V",
                program,
                frame));
        ib.ifCondition(
                AdvInsnBuilder.isFalse(AdvInsnBuilder.field(frame, frameLayout.returned)),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(
                                AdvInsnBuilder.constant("Unknown VM pc "),
                                AdvInsnBuilder.field(frame, frameLayout.programCounter)))));

        ib.returnValue(AdvInsnBuilder.field(frame, frameLayout.returnValue));
        return methodNode;
    }

    private MethodNode genExecuteSegmentedMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.VARARGS},
                "execute",
                "([ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>([ILjava/lang/Object;[Ljava/lang/Object;)TT;",
                null);
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        Local codeIds = ib.getLocal("codeIds", "[I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        ib.returnValue(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                "execute",
                "java/lang/Object",
                codeIds,
                receiver,
                arguments,
                AdvInsnBuilder.constant(integrityCapability)));
        return methodNode;
    }

    private MethodNode genExecuteSegmentedWithIntegrityMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC},
                "execute",
                "([ILjava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>([ILjava/lang/Object;[Ljava/lang/Object;I)TT;",
                null);
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        Local codeIds = ib.getLocal("codeIds", "[I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local integrityKey = ib.getLocal("integrityKey", "I", 3);
        Local firstProgram = ib.getLocal("firstProgram", programLayout.owner, 4);
        Local program = ib.getLocal("program", programLayout.owner, 5);
        Local frame = ib.getLocal("frame", frameLayout.owner, 6);
        Local argumentOffset = ib.getLocal("argumentOffset", "I", 7);
        Local index = ib.getLocal("segmentIndex", "I", 8);
        Local candidate = ib.getLocal("candidateProgram", programLayout.owner, 9);

        ib.set(firstProgram, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.resolve.name(),
                programLayout.owner,
                AdvInsnBuilder.arrayAt(codeIds, AdvInsnBuilder.constant(0))));
        ib.set(frame, AdvInsnBuilder.newObject(
                frameLayout.owner,
                AdvInsnBuilder.callVirtual(firstProgram, programLayout.owner, programLayout.maxLocals.name(), "I"),
                AdvInsnBuilder.callVirtual(firstProgram, programLayout.owner, programLayout.maxStack.name(), "I")));
        ib.set(
                AdvInsnBuilder.field(frame, frameLayout.integrityKey),
                AdvInsnBuilder.bitXor(integrityKey, AdvInsnBuilder.constant(integrityCapability)));

        ib.ifElse(
                AdvInsnBuilder.notNull(receiver),
                b -> {
                    b.setArray(AdvInsnBuilder.field(frame, frameLayout.locals), AdvInsnBuilder.constant(0), receiver);
                    b.set(argumentOffset, AdvInsnBuilder.constant(1));
                },
                b -> b.set(argumentOffset, AdvInsnBuilder.constant(0)));

        ib.directCall(AdvInsnBuilder.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                AdvInsnBuilder.cast(arguments, "java/lang/Object"),
                AdvInsnBuilder.constant(0),
                AdvInsnBuilder.cast(AdvInsnBuilder.field(frame, frameLayout.locals), "java/lang/Object"),
                argumentOffset,
                AdvInsnBuilder.arrayLength(arguments)));

        ib.directCall(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.syncState.name(),
                "V",
                firstProgram,
                frame,
                AdvInsnBuilder.field(frame, frameLayout.programCounter)));

        ib.whileLoop(
                AdvInsnBuilder.isFalse(AdvInsnBuilder.field(frame, frameLayout.returned)),
                loop -> {
                    loop.set(candidate, AdvInsnBuilder.nullValue(programLayout.owner));
                    loop.forLoop(
                            b -> b.set(index, AdvInsnBuilder.constant(0)),
                            AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(codeIds)),
                            b -> b.increment(index, 1),
                            b -> {
                                b.set(program, AdvInsnBuilder.callStatic(
                                        vmLayout.owner,
                                        vmLayout.resolve.name(),
                                        programLayout.owner,
                                        AdvInsnBuilder.arrayAt(codeIds, index)));
                                b.ifCondition(
                                        AdvInsnBuilder.notEqual(
                                                AdvInsnBuilder.callStatic(
                                                        vmLayout.owner,
                                                        vmLayout.instructionIndex.name(),
                                                        "I",
                                                        program,
                                                        frame,
                                                        AdvInsnBuilder.field(frame, frameLayout.programCounter)),
                                                AdvInsnBuilder.constant(-1)),
                                        found -> {
                                            found.set(candidate, program);
                                            found.breakLoop();
                                        });
                            });
                    loop.ifCondition(
                            AdvInsnBuilder.isNull(candidate),
                            b -> b.throwValue(AdvInsnBuilder.newObject(
                                    "java/lang/IllegalStateException",
                                    stringConcat(
                                            AdvInsnBuilder.constant("Unknown VM pc "),
                                            AdvInsnBuilder.field(frame, frameLayout.programCounter)))));
                    loop.directCall(AdvInsnBuilder.callStatic(
                            vmLayout.owner,
                            vmLayout.interpret.name(),
                            "V",
                            candidate,
                            frame));
                });

        ib.returnValue(AdvInsnBuilder.field(frame, frameLayout.returnValue));
        return methodNode;
    }

    private MethodNode genResolveMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolve.name(),
                vmLayout.resolve.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local resolved = ib.getLocal("resolved", programLayout.owner, 1);
        Local iterator = ib.getLocal("iterator", "java/util/Iterator", 2);
        Local candidate = ib.getLocal("candidate", programLayout.owner, 3);

        ib.set(resolved, AdvInsnBuilder.nullValue(programLayout.owner));
        ib.set(iterator, AdvInsnBuilder.callInterface(
                AdvInsnBuilder.staticField(vmLayout.codePools),
                "java/util/List",
                "iterator",
                "java/util/Iterator"));

        ib.whileLoop(
                AdvInsnBuilder.isTrue(AdvInsnBuilder.callInterface(
                        iterator,
                        "java/util/Iterator",
                        "hasNext",
                        "Z")),
                b -> {
                    Expr pool = AdvInsnBuilder.cast(
                            AdvInsnBuilder.callInterface(
                                    iterator,
                                    "java/util/Iterator",
                                    "next",
                                    "java/lang/Object"),
                            vmCodePoolGenerator.className());
                    b.set(candidate, AdvInsnBuilder.callInterface(
                            pool,
                            vmCodePoolGenerator.className(),
                            vmCodePoolGenerator.find.name(),
                            programLayout.owner,
                            codeId));
                    b.ifCondition(
                            AdvInsnBuilder.notNull(candidate),
                            found -> {
                                found.ifCondition(
                                        AdvInsnBuilder.notNull(resolved),
                                        duplicate -> throwExceptionWithInt(
                                                duplicate,
                                                "java/lang/IllegalStateException",
                                                "Duplicate code id: ",
                                                codeId));
                                found.set(resolved, candidate);
                            });
                });

        ib.ifCondition(
                AdvInsnBuilder.isNull(resolved),
                b -> throwExceptionWithInt(
                        b,
                        "java/lang/IllegalArgumentException",
                        "Unknown code id: ",
                        codeId));
        ib.returnValue(resolved);
        return method;
    }

    private MethodNode genConstantStringMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.constantString.name(),
                vmLayout.constantString.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 4);
        Local opcode = ib.getLocal("opcode", "I", 5);
        ib.returnValue(AdvInsnBuilder.cast(
                AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.resolveConstant.name(),
                        "java/lang/Object",
                        program,
                        AdvInsnBuilder.arrayAt(constants, index),
                        frame,
                        instructionIndex,
                        opcode),
                "java/lang/String"));
        return method;
    }

    private MethodNode genMethodTypeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.methodType.name(),
                vmLayout.methodType.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 0);
        Local cached = ib.getLocal("cached", "java/lang/invoke/MethodType", 1);

        ib.set(cached, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.methodTypes), descriptor), "java/lang/invoke/MethodType"));
        ib.ifCondition(AdvInsnBuilder.notNull(cached), b -> b.returnValue(cached));

        ib.set(cached, AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "java/lang/invoke/MethodType",
                descriptor,
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                        "java/lang/Class",
                        "getClassLoader",
                        "java/lang/ClassLoader")));
        ib.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodTypes), descriptor, cached));
        ib.returnValue(cached);
        return method;
    }

    private MethodNode genResolveConstantMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolveConstant.name(),
                vmLayout.resolveConstant.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local value = ib.getLocal("value", "java/lang/Object", 1);
        Local frame = ib.getLocal("frame", frameLayout.owner, 2);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 3);
        Local opcode = ib.getLocal("opcode", "I", 4);
        Local encoded = ib.getLocal("encoded", "[[I", 5);
        Local methodKey = ib.getLocal("constantMethodKey", "I", 6);
        Local stateKey = ib.getLocal("constantStateKey", "I", 7);
        Local virtualPc = ib.getLocal("constantVirtualPc", "I", 8);
        Local blockIndex = ib.getLocal("constantBlockIndex", "I", 9);
        Local path = ib.getLocal("constantPath", "I", 10);
        Local secondaryPath = ib.getLocal("constantSecondaryPath", "I", 11);
        Local binding = ib.getLocal("constantBinding", "I", 12);
        Local secondaryBinding = ib.getLocal("constantSecondaryBinding", "I", 13);
        Local index = ib.getLocal("constantVariantIndex", "I", 14);
        Local variant = ib.getLocal("constantVariant", "[I", 15);
        Local candidate = ib.getLocal("constantCandidate", "[I", 16);
        Local nonceA = ib.getLocal("constantNonceA", "I", 17);
        Local nonceB = ib.getLocal("constantNonceB", "I", 18);
        Local decoded = ib.getLocal("decodedConstant", "[I", 19);
        Local tag = ib.getLocal("constantTag", "I", 20);
        Local chars = ib.getLocal("constantChars", "[C", 21);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 22);
        Local loader = ib.getLocal("loader", "java/lang/ClassLoader", 23);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 24);
        Local streamMask = ib.getLocal("constantStreamMask", "I", 25);
        Local typeDescriptor = ib.getLocal("typeDescriptor", "[Ljava/lang/String;", 26);
        LabelNode resolveType = new LabelNode();

        ib.ifCondition(
                AdvInsnBuilder.isInstanceOf(value, "[Ljava/lang/String;"),
                b -> {
                    b.set(typeDescriptor, AdvInsnBuilder.cast(value, "[Ljava/lang/String;"));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(AdvInsnBuilder.arrayLength(typeDescriptor), AdvInsnBuilder.constant(1)),
                            invalid -> invalid.throwValue(AdvInsnBuilder.newObject(
                                    "java/lang/IllegalStateException",
                                    AdvInsnBuilder.constant("Invalid VM type constant"))));
                    b.set(descriptor, AdvInsnBuilder.arrayAt(typeDescriptor, AdvInsnBuilder.constant(0)));
                    b.gotoLabel(resolveType);
                });
        ib.ifCondition(AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(value, "[[I")), b -> b.returnValue(value));
        ib.set(encoded, AdvInsnBuilder.cast(value, "[[I"));
        ib.ifCondition(
                AdvInsnBuilder.equal(AdvInsnBuilder.arrayLength(encoded), AdvInsnBuilder.constant(0)),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        AdvInsnBuilder.constant("Invalid dynamic constant"))));

        ib.set(methodKey, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvInsnBuilder.field(frame, frameLayout.stateKey));
        ib.set(virtualPc, layoutValue(
                program,
                instructionIndex,
                ProtectedVMMethod.LAYOUT_PC,
                stateKey));
        ib.set(blockIndex, AdvInsnBuilder.field(frame, frameLayout.blockIndex));
        ib.set(path, mixCall(
                AdvInsnBuilder.bitXor(methodKey, stateKey),
                virtualPc,
                blockIndex,
                AdvInsnBuilder.constant(profile.saltConstant)));
        ib.set(binding, mixCall(
                path,
                instructionIndex,
                opcode,
                AdvInsnBuilder.constant(profile.saltString)));
        ib.set(secondaryPath, mixCall(
                AdvInsnBuilder.bitXor(stateKey, AdvInsnBuilder.constant(profile.saltArray)),
                methodKey,
                opcode,
                virtualPc));
        ib.set(secondaryBinding, mixCall(
                secondaryPath,
                blockIndex,
                instructionIndex,
                AdvInsnBuilder.constant(profile.saltOpcodeMap)));

        ib.set(variant, AdvInsnBuilder.nullValue("[I"));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(encoded)),
                b -> b.increment(index, 1),
                b -> b.ifCondition(
                        AdvInsnBuilder.isNull(variant),
                        unresolved -> {
                            unresolved.set(candidate, AdvInsnBuilder.arrayAt(encoded, index));
                            unresolved.ifCondition(
                                    AdvInsnBuilder.greaterOrEqual(
                                            AdvInsnBuilder.arrayLength(candidate),
                                            AdvInsnBuilder.constant(5)),
                                    sized -> {
                                        sized.set(nonceA, AdvInsnBuilder.arrayAt(candidate, AdvInsnBuilder.constant(0)));
                                        sized.set(nonceB, AdvInsnBuilder.arrayAt(candidate, AdvInsnBuilder.constant(1)));
                                        sized.ifCondition(
                                                AdvInsnBuilder.and(
                                                        AdvInsnBuilder.equal(
                                                                AdvInsnBuilder.arrayAt(candidate, AdvInsnBuilder.constant(2)),
                                                                mixCall(
                                                                        AdvInsnBuilder.bitXor(binding, nonceA),
                                                                        secondaryBinding,
                                                                        nonceB,
                                                                        AdvInsnBuilder.constant(profile.saltHandler))),
                                                        AdvInsnBuilder.equal(
                                                                AdvInsnBuilder.arrayAt(candidate, AdvInsnBuilder.constant(3)),
                                                                mixCall(
                                                                        AdvInsnBuilder.bitXor(secondaryBinding, nonceB),
                                                                        binding,
                                                                        nonceA,
                                                                        AdvInsnBuilder.constant(profile.saltBlock)))),
                                                found -> found.set(variant, candidate));
                                    });
                        }));
        ib.ifCondition(
                AdvInsnBuilder.isNull(variant),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        AdvInsnBuilder.constant("VM constant requested outside its execution state"))));
        ib.set(nonceA, AdvInsnBuilder.arrayAt(variant, AdvInsnBuilder.constant(0)));
        ib.set(nonceB, AdvInsnBuilder.arrayAt(variant, AdvInsnBuilder.constant(1)));
        ib.set(decoded, AdvInsnBuilder.newArray(
                "int",
                AdvInsnBuilder.minus(AdvInsnBuilder.arrayLength(variant), AdvInsnBuilder.constant(4))));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(decoded)),
                b -> b.increment(index, 1),
                b -> {
                    b.set(streamMask, AdvInsnBuilder.bitXor(
                            mixCall(
                                    AdvInsnBuilder.bitXor(binding, nonceA),
                                    secondaryBinding,
                                    index,
                                    AdvInsnBuilder.constant(profile.saltConstant)),
                            AdvInsnBuilder.callStatic(
                                    "java/lang/Integer",
                                    "rotateLeft",
                                    "I",
                                    mixCall(
                                            AdvInsnBuilder.bitXor(secondaryBinding, nonceB),
                                            binding,
                                            index,
                                            AdvInsnBuilder.constant(profile.saltArray)),
                                    AdvInsnBuilder.bitAnd(
                                            AdvInsnBuilder.plus(nonceA, index),
                                            AdvInsnBuilder.constant(31)))));
                    b.setArray(
                            decoded,
                            index,
                            AdvInsnBuilder.bitXor(
                                    AdvInsnBuilder.arrayAt(
                                            variant,
                                            AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(4))),
                                    streamMask));
                });
        ib.set(tag, AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(0)));
        ib.ifCondition(
                AdvInsnBuilder.equal(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_INTEGER)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Integer",
                        "valueOf",
                        "java/lang/Integer",
                        AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(1)))));
        ib.ifCondition(
                AdvInsnBuilder.equal(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_LONG)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Long",
                        "valueOf",
                        "java/lang/Long",
                        AdvInsnBuilder.bitOr(
                                AdvInsnBuilder.shiftLeft(
                                        AdvInsnBuilder.cast(AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(1)), "J"),
                                        AdvInsnBuilder.constant(32)),
                                AdvInsnBuilder.callStatic(
                                        "java/lang/Integer",
                                        "toUnsignedLong",
                                        "J",
                                        AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(2)))))));
        ib.ifCondition(
                AdvInsnBuilder.equal(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_FLOAT)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Float",
                        "valueOf",
                        "java/lang/Float",
                        AdvInsnBuilder.callStatic(
                                "java/lang/Float",
                                "intBitsToFloat",
                                "F",
                                AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(1))))));
        ib.ifCondition(
                AdvInsnBuilder.equal(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_DOUBLE)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Double",
                        "valueOf",
                        "java/lang/Double",
                        AdvInsnBuilder.callStatic(
                                "java/lang/Double",
                                "longBitsToDouble",
                                "D",
                                AdvInsnBuilder.bitOr(
                                                AdvInsnBuilder.shiftLeft(
                                                        AdvInsnBuilder.cast(AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(1)), "J"),
                                                        AdvInsnBuilder.constant(32)),
                                        AdvInsnBuilder.callStatic(
                                                "java/lang/Integer",
                                                "toUnsignedLong",
                                                "J",
                                                AdvInsnBuilder.arrayAt(decoded, AdvInsnBuilder.constant(2))))))));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.notEqual(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_STRING)),
                        AdvInsnBuilder.notEqual(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_TYPE))),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        AdvInsnBuilder.constant("Unknown dynamic constant type"))));
        ib.set(chars, AdvInsnBuilder.newArray(
                "char",
                AdvInsnBuilder.minus(AdvInsnBuilder.arrayLength(decoded), AdvInsnBuilder.constant(1))));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(1)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(decoded)),
                b -> b.increment(index, 1),
                b -> b.setArray(
                        chars,
                        AdvInsnBuilder.minus(index, AdvInsnBuilder.constant(1)),
                        AdvInsnBuilder.cast(AdvInsnBuilder.arrayAt(decoded, index), "C")));
        ib.set(descriptor, AdvInsnBuilder.newObject("java/lang/String", chars));
        ib.ifCondition(
                AdvInsnBuilder.equal(tag, AdvInsnBuilder.constant(ProtectedVMMethod.CONSTANT_STRING)),
                b -> b.returnValue(descriptor));
        ib.mark(resolveType, "resolveTypeConstant");
        ib.set(loader, AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                "java/lang/Class",
                "getClassLoader",
                "java/lang/ClassLoader"));
        ib.ifCondition(
                AdvInsnBuilder.greaterThan(AdvInsnBuilder.arrayLength(AdvInsnBuilder.field(frame, frameLayout.locals)), AdvInsnBuilder.constant(0)),
                b -> {
                    b.set(receiver, AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.locals), AdvInsnBuilder.constant(0)));
                    b.ifCondition(
                            AdvInsnBuilder.notNull(receiver),
                            bb -> bb.set(loader, AdvInsnBuilder.callVirtual(
                                    AdvInsnBuilder.callVirtual(receiver, "java/lang/Object", "getClass", "java/lang/Class"),
                                    "java/lang/Class",
                                    "getClassLoader",
                                    "java/lang/ClassLoader")));
                });

        ib.ifCondition(
                AdvInsnBuilder.equal(
                        AdvInsnBuilder.callVirtual(descriptor, "java/lang/String", "length", "I"),
                        AdvInsnBuilder.constant(0)),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        AdvInsnBuilder.constant("Invalid encoded VM type constant"))));
        ib.ifElse(
                AdvInsnBuilder.equal(
                        AdvInsnBuilder.callVirtual(descriptor, "java/lang/String", "charAt", "C", AdvInsnBuilder.constant(0)),
                        AdvInsnBuilder.constant('(')),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString",
                        "java/lang/invoke/MethodType",
                        descriptor,
                        loader)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.loadOwnerWithLoader.name(),
                        "java/lang/Class",
                        descriptor,
                        loader)));
        return method;
    }

    private MethodNode genFindExceptionHandlerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findExceptionHandler.name(),
                vmLayout.findExceptionHandler.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local throwable = ib.getLocal("throwable", "java/lang/Throwable", 0);
        Local handlers = ib.getLocal("handlers", "[I", 1);
        Local instructionPc = ib.getLocal("instructionPc", "I", 2);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 3);
        Local opcode = ib.getLocal("opcode", "I", 4);
        Local program = ib.getLocal("program", programLayout.owner, 5);
        Local frame = ib.getLocal("frame", frameLayout.owner, 6);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 7);
        Local methodKey = ib.getLocal("methodKey", "I", 8);
        Local index = ib.getLocal("index", "I", 9);
        Local handlerSlot = ib.getLocal("handlerSlot", "I", 10);
        Local startPc = ib.getLocal("startPc", "I", 11);
        Local endPc = ib.getLocal("endPc", "I", 12);
        Local handlerPc = ib.getLocal("handlerPc", "I", 13);
        Local typeIndex = ib.getLocal("typeIndex", "I", 14);

        ib.set(methodKey, callProgramInt(program, programLayout.methodKey.name()));

        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(handlers)),
                b -> b.increment(index, 4),
                b -> {
                    b.set(handlerSlot, AdvInsnBuilder.divide(index, AdvInsnBuilder.constant(ProtectedVMMethod.HANDLER_SIZE)));
                    b.set(startPc, AdvInsnBuilder.arrayAt(handlers, index));
                    b.set(endPc, AdvInsnBuilder.arrayAt(handlers, AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(1))));
                    b.set(handlerPc, AdvInsnBuilder.arrayAt(handlers, AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(2))));
                    b.set(typeIndex, AdvInsnBuilder.arrayAt(handlers, AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(3))));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(methodKey, AdvInsnBuilder.constant(0)),
                            decode -> {
                                decode.set(startPc, AdvInsnBuilder.bitXor(startPc, handlerMixCall(methodKey, handlerSlot, 0)));
                                decode.set(endPc, AdvInsnBuilder.bitXor(endPc, handlerMixCall(methodKey, handlerSlot, 1)));
                                decode.set(handlerPc, AdvInsnBuilder.bitXor(handlerPc, handlerMixCall(methodKey, handlerSlot, 2)));
                                decode.set(typeIndex, AdvInsnBuilder.bitXor(typeIndex, handlerMixCall(methodKey, handlerSlot, 3)));
                            });
                    b.ifCondition(
                            AdvInsnBuilder.and(
                                    AdvInsnBuilder.greaterOrEqual(instructionPc, startPc),
                                    AdvInsnBuilder.lessThan(instructionPc, endPc)),
                            inRange -> {
                                inRange.ifCondition(AdvInsnBuilder.lessThan(typeIndex, AdvInsnBuilder.constant(0)), catchAll -> catchAll.returnValue(handlerPc));
                                inRange.ifCondition(
                                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                                AdvInsnBuilder.callStatic(
                                                        vmLayout.owner,
                                                        vmLayout.loadOwner.name(),
                                                        "java/lang/Class",
                                                        AdvInsnBuilder.callStatic(
                                                                vmLayout.owner,
                                                                vmLayout.constantString.name(),
                                                                "java/lang/String",
                                                                program,
                                                                frame,
                                                                constants,
                                                                typeIndex,
                                                                instructionIndex,
                                                                opcode)),
                                                "java/lang/Class",
                                                "isInstance",
                                                "Z",
                                                AdvInsnBuilder.cast(throwable, "java/lang/Object"))),
                                        typeMatches -> typeMatches.returnValue(handlerPc));
                            });
                });
        ib.returnValue(AdvInsnBuilder.constant(-1));
        return method;
    }

    private MethodNode genGetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.getField.name(),
                vmLayout.getField.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        fieldHandle(owner, name, descriptor, isStatic, AdvInsnBuilder.constant(false)),
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        receiver)),
                "java/lang/Throwable",
                "throwable",
                (b) -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private MethodNode genSetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.setField.name(),
                vmLayout.setField.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        Local value = ib.getLocal("value", "java/lang/Object", 5);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 6);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 7);
        ib.tryCatch(
                b -> {
                    b.set(value, AdvInsnBuilder.callStatic(
                            vmLayout.owner,
                            vmLayout.coerceArgument.name(),
                            "java/lang/Object",
                            value,
                            AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", descriptor)));
                    b.ifCondition(
                            AdvInsnBuilder.isTrue(isStatic),
                            staticWrite -> {
                                staticWrite.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                                staticWrite.set(field, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", ownerClass, name));
                                staticWrite.ifCondition(
                                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callStatic(
                                                "java/lang/reflect/Modifier",
                                                "isFinal",
                                                "Z",
                                                AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "getModifiers", "I"))),
                                        finalWrite -> {
                                            finalWrite.directCall(AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvInsnBuilder.constant(true)));
                                            finalWrite.directCall(AdvInsnBuilder.callStatic(
                                                    vmLayout.owner,
                                                    vmLayout.unsafeSetStaticField.name(),
                                                    "V",
                                                    field,
                                                    value));
                                            finalWrite.returnVoid();
                                        });
                            });
                    b.directCall(AdvInsnBuilder.callVirtual(
                            fieldHandle(owner, name, descriptor, isStatic, AdvInsnBuilder.constant(true)),
                            "java/lang/invoke/MethodHandle",
                            "invokeExact",
                            "V",
                            receiver,
                            value));
                    b.returnVoid();
                },
                "java/lang/Throwable",
                "throwable",
                b -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private MethodNode genFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.fieldHandle.name(),
                vmLayout.fieldHandle.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local setter = ib.getLocal("setter", "Z", 4);
        Local key = ib.getLocal("key", "java/lang/String", 5);
        Local cached = ib.getLocal("cached", "java/lang/invoke/MethodHandle", 6);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 7);
        Local fieldType = ib.getLocal("fieldType", "java/lang/Class", 8);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 9);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 10);

        ib.set(key, fieldHandleKey(owner, name, descriptor, isStatic, setter));
        ib.set(cached, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.fieldHandles), key), "java/lang/invoke/MethodHandle"));
        ib.ifCondition(AdvInsnBuilder.notNull(cached), b -> b.returnValue(cached));

        ib.tryCatch(
                b -> {
                    b.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                    b.set(fieldType, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", descriptor));
                    b.set(field, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", ownerClass, name));
                    b.directCall(AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvInsnBuilder.constant(true)));

                    b.ifCondition(
                            AdvInsnBuilder.notEqual(
                                    AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "getType", "java/lang/Class"),
                                    fieldType),
                            mismatch -> throwNoSuchField(mismatch, ownerClass, name));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(
                                    AdvInsnBuilder.callStatic(
                                            "java/lang/reflect/Modifier",
                                            "isStatic",
                                            "Z",
                                            AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "getModifiers", "I")),
                                    isStatic),
                            mismatch -> throwNoSuchField(mismatch, ownerClass, name));

                    b.set(handle, AdvInsnBuilder.callStatic(
                            vmLayout.owner,
                            vmLayout.adaptFieldHandle.name(),
                            "java/lang/invoke/MethodHandle",
                            field,
                            isStatic,
                            setter));
                    b.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.fieldHandles), key, handle));
                    b.returnValue(handle);
                },
                "java/lang/ReflectiveOperationException",
                "exception",
                b -> b.throwValue(illegalStateException(b.getLocal("exception"))));
        return method;
    }

    private MethodNode genAdaptFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptFieldHandle.name(),
                vmLayout.adaptFieldHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);
        Local isStatic = ib.getLocal("isStatic", "Z", 1);
        Local setter = ib.getLocal("setter", "Z", 2);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 3);

        ib.ifElse(
                AdvInsnBuilder.isFalse(setter),
                b -> {
                    b.set(handle, AdvInsnBuilder.callVirtual(
                            AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                            "java/lang/invoke/MethodHandles$Lookup",
                            "unreflectGetter",
                            "java/lang/invoke/MethodHandle",
                            field));
                    b.ifCondition(AdvInsnBuilder.isTrue(isStatic), bb -> dropLeadingObjectArgument(bb, handle));
                    b.returnValue(AdvInsnBuilder.callVirtual(
                            handle,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "java/lang/invoke/MethodHandle",
                            getterHandleType()));
                },
                b -> {
                    b.set(handle, AdvInsnBuilder.callVirtual(
                            AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                            "java/lang/invoke/MethodHandles$Lookup",
                            "unreflectSetter",
                            "java/lang/invoke/MethodHandle",
                            field));
                    b.ifCondition(AdvInsnBuilder.isTrue(isStatic), bb -> dropLeadingObjectArgument(bb, handle));
                    b.returnValue(AdvInsnBuilder.callVirtual(
                            handle,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "java/lang/invoke/MethodHandle",
                            methodType(voidClass(), objectClass(), classArray(b, "setterParameters", objectClass()))));
                });
        return method;
    }

    private MethodNode genUnsafeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.unsafe.name(),
                vmLayout.unsafe.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);

        ib.tryCatch(
                b -> {
                    b.set(field, AdvInsnBuilder.callVirtual(
                            AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType("sun/misc/Unsafe")),
                            "java/lang/Class",
                            "getDeclaredField",
                            "java/lang/reflect/Field",
                            AdvInsnBuilder.constant("theUnsafe")));
                    b.directCall(AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvInsnBuilder.constant(true)));
                    b.returnValue(AdvInsnBuilder.cast(
                            AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "get", "java/lang/Object", AdvInsnBuilder.constant(null)),
                            "sun/misc/Unsafe"));
                },
                "java/lang/ReflectiveOperationException",
                "exception",
                b -> b.throwValue(illegalStateException(b.getLocal("exception"))));
        return method;
    }

    private MethodNode genUnsafeSetStaticFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.unsafeSetStaticField.name(),
                vmLayout.unsafeSetStaticField.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);
        Local value = ib.getLocal("value", "java/lang/Object", 1);
        Local unsafe = ib.getLocal("unsafe", "sun/misc/Unsafe", 2);
        Local base = ib.getLocal("base", "java/lang/Object", 3);
        Local offset = ib.getLocal("offset", "J", 4);
        Local type = ib.getLocal("type", "java/lang/Class", 6);

        ib.set(unsafe, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.unsafe.name(), "sun/misc/Unsafe"));
        ib.set(base, AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "staticFieldBase", "java/lang/Object", field));
        ib.set(offset, AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "staticFieldOffset", "J", field));
        ib.set(type, AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "getType", "java/lang/Class"));
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Boolean")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putBoolean", "V", base, offset, AdvInsnBuilder.unbox(value, "Z")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Character")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putChar", "V", base, offset, AdvInsnBuilder.unbox(value, "C")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Byte")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putByte", "V", base, offset, AdvInsnBuilder.unbox(value, "B")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Short")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putShort", "V", base, offset, AdvInsnBuilder.unbox(value, "S")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Integer")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putInt", "V", base, offset, AdvInsnBuilder.unbox(value, "I")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Long")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putLong", "V", base, offset, AdvInsnBuilder.unbox(value, "J")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Float")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putFloat", "V", base, offset, AdvInsnBuilder.unbox(value, "F")));
            b.returnVoid();
        });
        ib.ifCondition(AdvInsnBuilder.equal(type, primitiveType("java/lang/Double")), b -> {
            b.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putDouble", "V", base, offset, AdvInsnBuilder.unbox(value, "D")));
            b.returnVoid();
        });
        ib.directCall(AdvInsnBuilder.callVirtual(unsafe, "sun/misc/Unsafe", "putObject", "V", base, offset, value));
        ib.returnVoid();
        return method;
    }

    private MethodNode genFindFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findField.name(),
                vmLayout.findField.descriptor(),
                new String[]{"java/lang/NoSuchFieldException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local interfaces = ib.getLocal("interfaces", "[Ljava/lang/Class;", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local superClass = ib.getLocal("superClass", "java/lang/Class", 5);

        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        ownerClass,
                        "java/lang/Class",
                        "getDeclaredField",
                        "java/lang/reflect/Field",
                        name)),
                "java/lang/NoSuchFieldException",
                "ignored",
                b -> {});

        ib.set(interfaces, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getInterfaces", "[Ljava/lang/Class;"));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(interfaces)),
                b -> b.increment(index, 1),
                b -> b.tryCatch(
                        tryFind -> tryFind.returnValue(AdvInsnBuilder.callStatic(
                                vmLayout.owner,
                                vmLayout.findField.name(),
                                "java/lang/reflect/Field",
                                AdvInsnBuilder.arrayAt(interfaces, index),
                                name)),
                        "java/lang/NoSuchFieldException",
                        "ignored",
                        ignored -> {}));

        ib.set(superClass, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getSuperclass", "java/lang/Class"));
        ib.ifCondition(
                AdvInsnBuilder.notNull(superClass),
                b -> b.returnValue(AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", superClass, name)));
        ib.throwValue(AdvInsnBuilder.newObject("java/lang/NoSuchFieldException", name));
        return method;
    }

    private MethodNode genFindMethodMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findMethod.name(),
                vmLayout.findMethod.descriptor(),
                new String[]{"java/lang/NoSuchMethodException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local parameterTypes = ib.getLocal("parameterTypes", "[Ljava/lang/Class;", 2);
        Local returnType = ib.getLocal("returnType", "java/lang/Class", 3);
        Local declaredMethods = ib.getLocal("declaredMethods", "[Ljava/lang/reflect/Method;", 4);
        Local candidate = ib.getLocal("candidate", "java/lang/reflect/Method", 5);
        Local interfaces = ib.getLocal("interfaces", "[Ljava/lang/Class;", 6);
        Local index = ib.getLocal("index", "I", 7);
        Local superClass = ib.getLocal("superClass", "java/lang/Class", 9);

        ib.set(declaredMethods, AdvInsnBuilder.callVirtual(
                ownerClass,
                "java/lang/Class",
                "getDeclaredMethods",
                "[Ljava/lang/reflect/Method;"));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(declaredMethods)),
                b -> b.increment(index, 1),
                b -> {
                    b.set(candidate, AdvInsnBuilder.arrayAt(declaredMethods, index));
                    b.ifCondition(
                            AdvInsnBuilder.and(
                                    AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                            name,
                                            "java/lang/String",
                                            "equals",
                                            "Z",
                                            AdvInsnBuilder.cast(
                                                    AdvInsnBuilder.callVirtual(candidate, "java/lang/reflect/Method", "getName", "java/lang/String"),
                                                    "java/lang/Object"))),
                                    AdvInsnBuilder.and(
                                            AdvInsnBuilder.equal(
                                                    AdvInsnBuilder.callVirtual(candidate, "java/lang/reflect/Method", "getReturnType", "java/lang/Class"),
                                                    returnType),
                                            AdvInsnBuilder.isTrue(AdvInsnBuilder.callStatic(
                                                    "java/util/Arrays",
                                                    "equals",
                                                    "Z",
                                                    AdvInsnBuilder.cast(
                                                            AdvInsnBuilder.callVirtual(candidate, "java/lang/reflect/Method", "getParameterTypes", "[Ljava/lang/Class;"),
                                                            "[Ljava/lang/Object;"),
                                                    AdvInsnBuilder.cast(parameterTypes, "[Ljava/lang/Object;"))))),
                            found -> found.returnValue(candidate));
                });

        ib.set(interfaces, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getInterfaces", "[Ljava/lang/Class;"));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(interfaces)),
                b -> b.increment(index, 1),
                b -> b.tryCatch(
                        tryFind -> tryFind.returnValue(AdvInsnBuilder.callStatic(
                                vmLayout.owner,
                                vmLayout.findMethod.name(),
                                "java/lang/reflect/Method",
                                AdvInsnBuilder.arrayAt(interfaces, index),
                                name,
                                parameterTypes,
                                returnType)),
                        "java/lang/NoSuchMethodException",
                        "ignored",
                        ignored -> {}));

        ib.set(superClass, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getSuperclass", "java/lang/Class"));
        ib.ifCondition(
                AdvInsnBuilder.notNull(superClass),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        vmLayout.owner,
                        vmLayout.findMethod.name(),
                        "java/lang/reflect/Method",
                        superClass,
                        name,
                        parameterTypes,
                        returnType)));
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/NoSuchMethodException",
                stringConcat(
                        AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvInsnBuilder.constant("."),
                        name)));
        return method;
    }

    private MethodNode genInvokeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.invoke.name(),
                vmLayout.invoke.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 5);
        Local key = ib.getLocal("key", "java/lang/String", 6);
        Local target = ib.getLocal("target", "java/lang/invoke/MethodHandle", 7);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 8);
        Local exception = ib.getLocal("exception", "java/lang/Throwable", 9);
        Local reflectedMethod = ib.getLocal("reflectedMethod", "java/lang/reflect/Method", 10);

        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.isFalse(isStatic),
                        AdvInsnBuilder.and(
                                AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvInsnBuilder.cast(AdvInsnBuilder.constant("clone"), "java/lang/Object"))),
                                AdvInsnBuilder.and(
                                        AdvInsnBuilder.equal(
                                                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"),
                                                AdvInsnBuilder.constant(0)),
                                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                                AdvInsnBuilder.callVirtual(receiver, "java/lang/Object", "getClass", "java/lang/Class"),
                                                "java/lang/Class",
                                                "isArray",
                                                "Z"))))),
                b -> b.returnValue(AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.cloneArray.name(), "java/lang/Object", receiver)));

        ib.ifCondition(
                isMethodHandleInvoke(owner, name, isStatic),
                b -> {
                    coerceArguments(b, arguments, methodType);
                    b.tryCatch(
                            invokeMethodHandle -> invokeMethodHandle.returnValue(AdvInsnBuilder.callVirtual(
                                    asArrayInvokerHandle(
                                            AdvInsnBuilder.callVirtual(
                                                    AdvInsnBuilder.callVirtual(
                                                            AdvInsnBuilder.cast(receiver, "java/lang/invoke/MethodHandle"),
                                                            "java/lang/invoke/MethodHandle",
                                                            "asType",
                                                            "java/lang/invoke/MethodHandle",
                                                            methodType),
                                                    "java/lang/invoke/MethodHandle",
                                                    "asSpreader",
                                                    "java/lang/invoke/MethodHandle",
                                                    objectArrayClass(),
                                                    AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"))),
                                    "java/lang/invoke/MethodHandle",
                                    "invokeExact",
                                    "java/lang/Object",
                                    arguments)),
                            "java/lang/Throwable",
                            "throwable",
                            caught -> caught.throwValue(rethrow(caught.getLocal("throwable"))));
                });

        ib.set(key, methodHandleKey(owner, name, methodType, isStatic));
        ib.set(target, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.methodHandles), key), "java/lang/invoke/MethodHandle"));

        ib.ifCondition(
                AdvInsnBuilder.isNull(target),
                b -> {
                    b.set(ownerClass, AdvInsnBuilder.nullValue("java/lang/Class"));
                    b.set(exception, AdvInsnBuilder.nullValue("java/lang/Throwable"));
                    b.tryCatch(
                            tryReflect -> {
                                tryReflect.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                                tryReflect.set(reflectedMethod, AdvInsnBuilder.callStatic(
                                        vmLayout.owner,
                                        vmLayout.findMethod.name(),
                                        "java/lang/reflect/Method",
                                        ownerClass,
                                        name,
                                        AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;"),
                                        AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "returnType", "java/lang/Class")));
                                cacheAdaptedMethodHandle(tryReflect, reflectedMethod, ownerClass, name, methodType, isStatic, key, target);
                            },
                            "java/lang/Throwable",
                            "caught",
                            caught -> caught.set(exception, caught.getLocal("caught")));

                    b.ifCondition(
                            AdvInsnBuilder.and(AdvInsnBuilder.isNull(target), AdvInsnBuilder.isInstanceOf(exception, "java/lang/NoSuchMethodException")),
                            publicLookup -> publicLookup.tryCatch(
                                    tryPublic -> {
                                        tryPublic.set(reflectedMethod, AdvInsnBuilder.callVirtual(
                                                ownerClass,
                                                "java/lang/Class",
                                                "getMethod",
                                                "java/lang/reflect/Method",
                                                name,
                                                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                                        cacheAdaptedMethodHandle(tryPublic, reflectedMethod, ownerClass, name, methodType, isStatic, key, target);
                                    },
                                    "java/lang/ReflectiveOperationException",
                                    "caught",
                                    caught -> caught.set(exception, caught.getLocal("caught"))));

                    b.ifCondition(
                            AdvInsnBuilder.isNull(target),
                            miss -> miss.ifElse(
                                    AdvInsnBuilder.isInstanceOf(exception, "java/lang/reflect/InaccessibleObjectException"),
                                    direct -> {
                                        direct.set(target, AdvInsnBuilder.callStatic(
                                                vmLayout.owner,
                                                vmLayout.adaptDirectMethodHandle.name(),
                                                "java/lang/invoke/MethodHandle",
                                                ownerClass,
                                                name,
                                                methodType,
                                                isStatic));
                                        direct.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodHandles), key, target));
                                    },
                                    failure -> failure.throwValue(illegalStateException(exception))));
                });

        coerceArguments(ib, arguments, methodType);
        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        target,
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        receiver,
                        arguments)),
                "java/lang/Throwable",
                "throwable",
                b -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private static Condition isMethodHandleInvoke(Local owner, Local name, Local isStatic)
    {
        return AdvInsnBuilder.and(
                AdvInsnBuilder.isFalse(isStatic),
                AdvInsnBuilder.and(
                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                owner,
                                "java/lang/String",
                                "equals",
                                "Z",
                                AdvInsnBuilder.cast(AdvInsnBuilder.constant("java/lang/invoke/MethodHandle"), "java/lang/Object"))),
                        AdvInsnBuilder.or(
                                AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvInsnBuilder.cast(AdvInsnBuilder.constant("invoke"), "java/lang/Object"))),
                                AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvInsnBuilder.cast(AdvInsnBuilder.constant("invokeExact"), "java/lang/Object"))))));
    }

    private MethodNode genConstructMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.construct.name(),
                vmLayout.construct.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local key = ib.getLocal("key", "java/lang/String", 3);
        Local target = ib.getLocal("target", "java/lang/invoke/MethodHandle", 4);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 5);
        Local constructor = ib.getLocal("constructor", "java/lang/reflect/Constructor", 6);

        ib.set(key, stringConcat(AdvInsnBuilder.constant("<init>:"), owner, methodType));
        ib.set(target, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.methodHandles), key), "java/lang/invoke/MethodHandle"));

        ib.ifCondition(
                AdvInsnBuilder.isNull(target),
                b -> b.tryCatch(
                        resolve -> {
                            resolve.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                            resolve.set(constructor, AdvInsnBuilder.callVirtual(
                                    ownerClass,
                                    "java/lang/Class",
                                    "getDeclaredConstructor",
                                    "java/lang/reflect/Constructor",
                                    AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                            resolve.directCall(AdvInsnBuilder.callVirtual(constructor, "java/lang/reflect/Constructor", "setAccessible", "V", AdvInsnBuilder.constant(true)));
                            resolve.set(target, AdvInsnBuilder.callStatic(
                                    vmLayout.owner,
                                    vmLayout.adaptConstructorHandle.name(),
                                    "java/lang/invoke/MethodHandle",
                                    constructor,
                                    AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
                            resolve.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodHandles), key, target));
                        },
                        "java/lang/Throwable",
                        "throwable",
                        caught -> caught.throwValue(rethrow(caught.getLocal("throwable")))));

        coerceArguments(ib, arguments, methodType);
        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        target,
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        AdvInsnBuilder.nullValue("java/lang/Object"),
                        arguments)),
                "java/lang/Throwable",
                "throwable",
                b -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private MethodNode genAdaptMethodHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptMethodHandle.name(),
                vmLayout.adaptMethodHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local methodRef = ib.getLocal("method", "java/lang/reflect/Method", 0);
        Local isStatic = ib.getLocal("isStatic", "Z", 1);
        Local parameterCount = ib.getLocal("parameterCount", "I", 2);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 3);

        ib.set(handle, AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(
                                AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                                "java/lang/invoke/MethodHandles$Lookup",
                                "unreflect",
                                "java/lang/invoke/MethodHandle",
                                methodRef),
                        "java/lang/invoke/MethodHandle",
                        "asFixedArity",
                        "java/lang/invoke/MethodHandle"),
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "java/lang/invoke/MethodHandle",
                objectArrayClass(),
                parameterCount));
        ib.ifCondition(AdvInsnBuilder.isTrue(isStatic), b -> dropLeadingObjectArgument(b, handle));
        ib.returnValue(asInvokerHandle(handle, classArray(ib, "invokerParameters", objectArrayClass())));
        return method;
    }

    private MethodNode genAdaptDirectMethodHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptDirectMethodHandle.name(),
                vmLayout.adaptDirectMethodHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException", "java/lang/NoSuchMethodException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local lookup = ib.getLocal("lookup", "java/lang/invoke/MethodHandles$Lookup", 4);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 5);

        ib.set(lookup, AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodHandles",
                "privateLookupIn",
                "java/lang/invoke/MethodHandles$Lookup",
                ownerClass,
                AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup")));
        ib.ifElse(
                AdvInsnBuilder.isTrue(isStatic),
                b -> {
                    b.set(handle, directHandle(lookup, ownerClass, name, methodType, true));
                    dropLeadingObjectArgument(b, handle);
                    b.returnValue(asInvokerHandle(handle, classArray(b, "staticInvokerParameters", objectArrayClass())));
                },
                b -> {
                    b.set(handle, directHandle(lookup, ownerClass, name, methodType, false));
                    b.returnValue(asInvokerHandle(handle, classArray(b, "virtualInvokerParameters", objectArrayClass())));
                });
        return method;
    }

    private MethodNode genAdaptConstructorHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptConstructorHandle.name(),
                vmLayout.adaptConstructorHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local constructor = ib.getLocal("constructor", "java/lang/reflect/Constructor", 0);
        Local parameterCount = ib.getLocal("parameterCount", "I", 1);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 2);

        ib.set(handle, AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(
                                AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                                "java/lang/invoke/MethodHandles$Lookup",
                                "unreflectConstructor",
                                "java/lang/invoke/MethodHandle",
                                constructor),
                        "java/lang/invoke/MethodHandle",
                        "asFixedArity",
                        "java/lang/invoke/MethodHandle"),
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "java/lang/invoke/MethodHandle",
                objectArrayClass(),
                parameterCount));
        dropLeadingObjectArgument(ib, handle);
        ib.returnValue(asInvokerHandle(handle, classArray(ib, "constructorInvokerParameters", objectArrayClass())));
        return method;
    }

    private MethodNode genCoerceArgumentMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.coerceArgument.name(),
                vmLayout.coerceArgument.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "java/lang/Object", 0);
        Local targetType = ib.getLocal("targetType", "java/lang/Class", 1);

        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Boolean")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Boolean", "valueOf", "java/lang/Boolean", AdvInsnBuilder.unbox(value, "Z"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Character")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Character", "valueOf", "java/lang/Character", AdvInsnBuilder.cast(AdvInsnBuilder.unbox(value, "I"), "C"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Byte")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Byte", "valueOf", "java/lang/Byte", AdvInsnBuilder.cast(AdvInsnBuilder.unbox(value, "I"), "B"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Short")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Short", "valueOf", "java/lang/Short", AdvInsnBuilder.cast(AdvInsnBuilder.unbox(value, "I"), "S"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Integer")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Integer", "valueOf", "java/lang/Integer", AdvInsnBuilder.unbox(value, "I"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Long")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Long", "valueOf", "java/lang/Long", AdvInsnBuilder.callVirtual(AdvInsnBuilder.cast(value, "java/lang/Number"), "java/lang/Number", "longValue", "J"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Float")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Float", "valueOf", "java/lang/Float", AdvInsnBuilder.callVirtual(AdvInsnBuilder.cast(value, "java/lang/Number"), "java/lang/Number", "floatValue", "F"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Double")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Double", "valueOf", "java/lang/Double", AdvInsnBuilder.callVirtual(AdvInsnBuilder.cast(value, "java/lang/Number"), "java/lang/Number", "doubleValue", "D"))));
        ib.returnValue(value);
        return method;
    }

    private MethodNode genCloneArrayMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.cloneArray.name(),
                vmLayout.cloneArray.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local array = ib.getLocal("array", "java/lang/Object", 0);
        Local length = ib.getLocal("length", "I", 1);
        Local clone = ib.getLocal("clone", "java/lang/Object", 2);

        ib.set(length, AdvInsnBuilder.callStatic("java/lang/reflect/Array", "getLength", "I", array));
        ib.set(clone, AdvInsnBuilder.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(array, "java/lang/Object", "getClass", "java/lang/Class"),
                        "java/lang/Class",
                        "getComponentType",
                        "java/lang/Class"),
                length));
        ib.directCall(AdvInsnBuilder.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                array,
                AdvInsnBuilder.constant(0),
                clone,
                AdvInsnBuilder.constant(0),
                length));
        ib.returnValue(clone);
        return method;
    }

    private MethodNode genLoadOwnerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.loadOwner.name(),
                vmLayout.loadOwner.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        ib.returnValue(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.loadOwnerWithLoader.name(),
                "java/lang/Class",
                owner,
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                        "java/lang/Class",
                        "getClassLoader",
                        "java/lang/ClassLoader")));
        return method;
    }

    private MethodNode genLoadOwnerWithLoaderMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.loadOwnerWithLoader.name(),
                vmLayout.loadOwnerWithLoader.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local loader = ib.getLocal("loader", "java/lang/ClassLoader", 1);
        Local normalized = ib.getLocal("normalized", "java/lang/String", 2);

        ib.ifCondition(
                AdvInsnBuilder.equal(AdvInsnBuilder.callVirtual(owner, "java/lang/String", "length", "I"), AdvInsnBuilder.constant(1)),
                b -> b.switchLookup(
                        AdvInsnBuilder.callVirtual(owner, "java/lang/String", "charAt", "C", AdvInsnBuilder.constant(0)),
                        null,
                        AdvInsnBuilder.switchCase('Z', bb -> bb.returnValue(primitiveType("java/lang/Boolean"))),
                        AdvInsnBuilder.switchCase('C', bb -> bb.returnValue(primitiveType("java/lang/Character"))),
                        AdvInsnBuilder.switchCase('B', bb -> bb.returnValue(primitiveType("java/lang/Byte"))),
                        AdvInsnBuilder.switchCase('S', bb -> bb.returnValue(primitiveType("java/lang/Short"))),
                        AdvInsnBuilder.switchCase('I', bb -> bb.returnValue(primitiveType("java/lang/Integer"))),
                        AdvInsnBuilder.switchCase('F', bb -> bb.returnValue(primitiveType("java/lang/Float"))),
                        AdvInsnBuilder.switchCase('J', bb -> bb.returnValue(primitiveType("java/lang/Long"))),
                        AdvInsnBuilder.switchCase('D', bb -> bb.returnValue(primitiveType("java/lang/Double"))),
                        AdvInsnBuilder.switchCase('V', bb -> bb.returnValue(primitiveType("java/lang/Void")))));

        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(owner, "java/lang/String", "startsWith", "Z", AdvInsnBuilder.constant("L"))),
                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(owner, "java/lang/String", "endsWith", "Z", AdvInsnBuilder.constant(";")))),
                b -> b.set(owner, AdvInsnBuilder.callVirtual(
                        owner,
                        "java/lang/String",
                        "substring",
                        "java/lang/String",
                        AdvInsnBuilder.constant(1),
                        AdvInsnBuilder.minus(
                                AdvInsnBuilder.callVirtual(owner, "java/lang/String", "length", "I"),
                                AdvInsnBuilder.constant(1)))));

        ib.set(normalized, AdvInsnBuilder.callVirtual(
                owner,
                "java/lang/String",
                "replace",
                "java/lang/String",
                AdvInsnBuilder.constant('/'),
                AdvInsnBuilder.constant('.')));
        ib.tryCatch(
                b -> b.returnValue(classForName(normalized, loader)),
                "java/lang/ClassNotFoundException",
                "exception",
                b -> b.tryCatch(
                        fallback -> fallback.returnValue(classForName(
                                normalized,
                                AdvInsnBuilder.callVirtual(
                                        AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                                        "java/lang/Class",
                                        "getClassLoader",
                                        "java/lang/ClassLoader"))),
                        "java/lang/ClassNotFoundException",
                        "fallbackException",
                        fallback -> fallback.throwValue(illegalStateException(b.getLocal("exception")))));
        return method;
    }

    private MethodNode genRethrowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.rethrow.name(),
                vmLayout.rethrow.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.throwValue(ib.getLocal("throwable", "java/lang/Throwable", 0));
        return method;
    }

    private MethodNode genMonitorForMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNCHRONIZED},
                vmLayout.monitorFor.name(),
                vmLayout.monitorFor.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        Local lock = ib.getLocal("lock", "java/util/concurrent/locks/ReentrantLock", 1);

        ib.ifCondition(AdvInsnBuilder.isNull(monitor), b -> b.throwValue(AdvInsnBuilder.newObject("java/lang/NullPointerException")));
        ib.set(lock, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.monitors), monitor), "java/util/concurrent/locks/ReentrantLock"));
        ib.ifCondition(
                AdvInsnBuilder.isNull(lock),
                b -> {
                    b.set(lock, AdvInsnBuilder.newObject("java/util/concurrent/locks/ReentrantLock"));
                    b.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.monitors), monitor, lock));
                });
        ib.returnValue(lock);
        return method;
    }

    private MethodNode genMonitorEnterMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.monitorEnter.name(),
                vmLayout.monitorEnter.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        ib.directCall(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.monitorFor.name(), "java/util/concurrent/locks/ReentrantLock", monitor),
                "java/util/concurrent/locks/ReentrantLock",
                "lock",
                "V"));
        ib.returnVoid();
        return method;
    }

    private MethodNode genMonitorExitMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.monitorExit.name(),
                vmLayout.monitorExit.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        ib.directCall(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.monitorFor.name(), "java/util/concurrent/locks/ReentrantLock", monitor),
                "java/util/concurrent/locks/ReentrantLock",
                "unlock",
                "V"));
        ib.returnVoid();
        return method;
    }

    private MethodNode genClInitMethod(List<CodePoolGenerator> codePoolGenerators)
    {
        MethodNode initMethod = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvInsnBuilder ib = new AdvInsnBuilder(initMethod);

        Local codePools = ib.var("codePools", "[Ljava/lang/Object;");
        ib.set(codePools, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(codePoolGenerators.size())));
        for (int i = 0; i < codePoolGenerators.size(); i++)
        {
            ib.setArray(
                    codePools,
                    AdvInsnBuilder.constant(i),
                    AdvInsnBuilder.staticField(codePoolGenerators.get(i).layout.instance));
        }
        ib.set(AdvInsnBuilder.staticField(vmLayout.codePools), AdvInsnBuilder.callStatic(
                "java/util/Arrays",
                "asList",
                "java/util/List",
                codePools));

        ib.set(AdvInsnBuilder.staticField(vmLayout.fieldHandles), AdvInsnBuilder.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvInsnBuilder.staticField(vmLayout.methodHandles), AdvInsnBuilder.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvInsnBuilder.staticField(vmLayout.methodTypes), AdvInsnBuilder.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvInsnBuilder.staticField(vmLayout.monitors), AdvInsnBuilder.callStatic(
                "java/util/Collections",
                "synchronizedMap",
                "java/util/Map",
                AdvInsnBuilder.cast(AdvInsnBuilder.newObject("java/util/WeakHashMap"), "java/util/Map")));
        structureGeneration.emitClassInitializers(ib);
        ib.returnVoid();
        return initMethod;
    }

    private static void register(InterpretBranch branch)
    {
        for (Opcs opcode : branch.opcodes())
        {
            InterpretBranch previous = branches.put(opcode, branch);

            if (previous != null)
            {
                throw new IllegalStateException(opcode + " is handled by both " + previous.getClass().getName() + " and " + branch.getClass().getName());
            }
        }
    }

    private static Expr mapGet(Expr map, Expr key)
    {
        return AdvInsnBuilder.callInterface(
                map,
                "java/util/Map",
                "get",
                "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"));
    }

    private static Expr mapPut(Expr map, Expr key, Expr value)
    {
        return AdvInsnBuilder.callInterface(
                map,
                "java/util/Map",
                "put",
                "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"),
                AdvInsnBuilder.cast(value, "java/lang/Object"));
    }

    private Expr callProgramArray(Expr program, String methodName)
    {
        return AdvInsnBuilder.callVirtual(program, programLayout.owner, methodName, "[I");
    }

    private Expr callProgramInt(Expr program, String methodName)
    {
        return AdvInsnBuilder.callVirtual(program, programLayout.owner, methodName, "I");
    }

    private Condition featureEnabled(Expr program, int flag)
    {
        return AdvInsnBuilder.notEqual(
                AdvInsnBuilder.bitAnd(
                        callProgramInt(program, programLayout.featureFlags.name()),
                        AdvInsnBuilder.constant(flag)),
                AdvInsnBuilder.constant(0));
    }

    private Expr layoutValue(Expr program, Expr instructionIndex, int logicalField, Expr stateKey)
    {
        return AdvInsnBuilder.callStatic(
                className(),
                vmLayout.layoutValue.name(),
                "I",
                program,
                instructionIndex,
                AdvInsnBuilder.constant(profile.layoutSlot(logicalField)),
                stateKey);
    }

    private Expr blockValue(Expr program, Expr blockIndex, Expr field)
    {
        return AdvInsnBuilder.callStatic(
                className(),
                vmLayout.blockValue.name(),
                "I",
                program,
                blockIndex,
                field);
    }

    private Expr mixCall(Expr key, Expr a, Expr b, Expr c)
    {
        return AdvInsnBuilder.callStatic(className(), vmLayout.mix.name(), "I", key, a, b, c);
    }

    private Expr handlerMixCall(Expr methodKey, Expr handlerSlot, int field)
    {
        return mixCall(
                methodKey,
                handlerSlot,
                AdvInsnBuilder.constant(field),
                AdvInsnBuilder.constant(profile.saltHandler));
    }

    private Expr dispatchKeyExpr(Expr opcode)
    {
        return mixCall(
                AdvInsnBuilder.constant(profile.dispatchSalt),
                opcode,
                AdvInsnBuilder.constant(profile.saltOpcode),
                AdvInsnBuilder.constant(0));
    }

    private int dispatchKey(int opcode)
    {
        return profile.dispatchKey(opcode);
    }

    private static void mixRound(AdvInsnBuilder ib, Local x, Expr value, int salt)
    {
        ib.set(x, AdvInsnBuilder.bitXor(
                x,
                add(
                        value,
                        AdvInsnBuilder.constant(salt),
                        AdvInsnBuilder.shiftLeft(x, AdvInsnBuilder.constant(6)),
                        AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(2)))));
    }

    private static Expr add(Expr first, Expr... rest)
    {
        Expr result = first;
        for (Expr value : rest)
        {
            result = AdvInsnBuilder.plus(result, value);
        }
        return result;
    }

    private Expr fieldHandle(Expr owner, Expr name, Expr descriptor, Expr isStatic, Expr setter)
    {
        return AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.fieldHandle.name(),
                "java/lang/invoke/MethodHandle",
                owner,
                name,
                descriptor,
                isStatic,
                setter);
    }

    private static Expr fieldHandleKey(Expr owner, Expr name, Expr descriptor, Expr isStatic, Expr setter)
    {
        return stringConcat(
                owner,
                AdvInsnBuilder.constant("."),
                name,
                AdvInsnBuilder.constant(":"),
                descriptor,
                AdvInsnBuilder.constant(":"),
                isStatic,
                AdvInsnBuilder.constant(":"),
                setter);
    }

    private static Expr methodHandleKey(Expr owner, Expr name, Expr methodType, Expr isStatic)
    {
        return stringConcat(
                owner,
                AdvInsnBuilder.constant("."),
                name,
                methodType,
                AdvInsnBuilder.constant(":"),
                isStatic);
    }

    private Expr rethrow(Expr throwable)
    {
        return AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.rethrow.name(),
                "java/lang/RuntimeException",
                throwable);
    }

    private static void throwNoSuchField(AdvInsnBuilder ib, Expr ownerClass, Expr fieldName)
    {
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/NoSuchFieldException",
                stringConcat(
                        AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvInsnBuilder.constant("."),
                        fieldName)));
    }

    private static void throwNoSuchMethod(AdvInsnBuilder ib, Expr ownerClass, Expr methodName, Expr methodType)
    {
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/NoSuchMethodException",
                stringConcat(
                        AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvInsnBuilder.constant("."),
                        methodName,
                        methodType)));
    }

    private static void throwExceptionWithInt(AdvInsnBuilder ib, String exceptionType, String prefix, Expr value)
    {
        ib.throwValue(AdvInsnBuilder.newObject(
                exceptionType,
                stringConcat(AdvInsnBuilder.constant(prefix), value)));
    }

    private static Expr illegalStateException(Expr cause)
    {
        return AdvInsnBuilder.newObject(
                "java/lang/IllegalStateException",
                AdvInsnBuilder.cast(cause, "java/lang/Throwable"));
    }

    private static Expr classForName(Expr name, Expr loader)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/Class",
                "forName",
                "java/lang/Class",
                name,
                AdvInsnBuilder.constant(false),
                loader);
    }

    private void cacheAdaptedMethodHandle(
            AdvInsnBuilder ib,
            Local reflectedMethod,
            Local ownerClass,
            Local name,
            Local methodType,
            Local isStatic,
            Local key,
            Local target)
    {
        ib.directCall(AdvInsnBuilder.callVirtual(reflectedMethod, "java/lang/reflect/Method", "setAccessible", "V", AdvInsnBuilder.constant(true)));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(
                        AdvInsnBuilder.callVirtual(reflectedMethod, "java/lang/reflect/Method", "getReturnType", "java/lang/Class"),
                        AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "returnType", "java/lang/Class")),
                b -> throwNoSuchMethod(b, ownerClass, name, methodType));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(
                        AdvInsnBuilder.callStatic(
                                "java/lang/reflect/Modifier",
                                "isStatic",
                                "Z",
                                AdvInsnBuilder.callVirtual(reflectedMethod, "java/lang/reflect/Method", "getModifiers", "I")),
                        isStatic),
                b -> throwNoSuchMethod(b, ownerClass, name, methodType));
        ib.set(target, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.adaptMethodHandle.name(),
                "java/lang/invoke/MethodHandle",
                reflectedMethod,
                isStatic,
                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
        ib.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodHandles), key, target));
    }

    private static Expr directHandle(Expr lookup, Expr ownerClass, Expr name, Expr methodType, boolean staticMethod)
    {
        return AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(
                                lookup,
                                "java/lang/invoke/MethodHandles$Lookup",
                                staticMethod ? "findStatic" : "findVirtual",
                                "java/lang/invoke/MethodHandle",
                                ownerClass,
                                name,
                                methodType),
                        "java/lang/invoke/MethodHandle",
                        "asFixedArity",
                        "java/lang/invoke/MethodHandle"),
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "java/lang/invoke/MethodHandle",
                objectArrayClass(),
                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"));
    }

    private static Expr asInvokerHandle(Expr handle, Expr parameterTypes)
    {
        return AdvInsnBuilder.callVirtual(
                handle,
                "java/lang/invoke/MethodHandle",
                "asType",
                "java/lang/invoke/MethodHandle",
                methodType(objectClass(), objectClass(), parameterTypes));
    }

    private static Expr asArrayInvokerHandle(Expr handle)
    {
        return AdvInsnBuilder.callVirtual(
                handle,
                "java/lang/invoke/MethodHandle",
                "asType",
                "java/lang/invoke/MethodHandle",
                AdvInsnBuilder.callStatic(
                        "java/lang/invoke/MethodType",
                        "methodType",
                        "java/lang/invoke/MethodType",
                        objectClass(),
                        objectArrayClass()));
    }

    private void coerceArguments(AdvInsnBuilder ib, Local arguments, Local methodType)
    {
        Local index = ib.var("argumentIndex", "I");
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(arguments)),
                b -> b.increment(index, 1),
                b -> b.setArray(
                        arguments,
                        index,
                        AdvInsnBuilder.callStatic(
                                vmLayout.owner,
                                vmLayout.coerceArgument.name(),
                                "java/lang/Object",
                                AdvInsnBuilder.arrayAt(arguments, index),
                                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterType", "java/lang/Class", index))));
    }

    private static Expr stringConcat(Expr first, Expr... rest)
    {
        Expr builder = AdvInsnBuilder.newObject("java/lang/StringBuilder", first);
        for (Expr value : rest)
        {
            if ((value.type().getSort() == org.objectweb.asm.Type.OBJECT || value.type().getSort() == org.objectweb.asm.Type.ARRAY)
                && !value.type().equals(org.objectweb.asm.Type.getType(String.class)))
            {
                value = AdvInsnBuilder.cast(value, "java/lang/Object");
            }
            builder = AdvInsnBuilder.callVirtual(
                    builder,
                    "java/lang/StringBuilder",
                    "append",
                    "java/lang/StringBuilder",
                    value);
        }
        return AdvInsnBuilder.callVirtual(
                builder,
                "java/lang/StringBuilder",
                "toString",
                "java/lang/String");
    }

    private static Expr objectClass()
    {
        return AdvInsnBuilder.constant(org.objectweb.asm.Type.getType("Ljava/lang/Object;"));
    }

    private static Expr objectArrayClass()
    {
        return AdvInsnBuilder.constant(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
    }

    private static Expr voidClass()
    {
        return AdvInsnBuilder.staticField("java/lang/Void", "TYPE", "java/lang/Class");
    }

    private static Expr primitiveType(String wrapper)
    {
        return AdvInsnBuilder.staticField(wrapper, "TYPE", "java/lang/Class");
    }

    private static Local classArray(AdvInsnBuilder ib, String name, Expr... values)
    {
        Local array = ib.var(name, "[Ljava/lang/Class;");
        ib.set(array, AdvInsnBuilder.newArray("java/lang/Class", AdvInsnBuilder.constant(values.length)));
        for (int i = 0; i < values.length; i++)
        {
            ib.setArray(array, AdvInsnBuilder.constant(i), values[i]);
        }
        return array;
    }

    private static Expr getterHandleType()
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "java/lang/invoke/MethodType",
                objectClass(),
                objectClass());
    }

    private static Expr methodType(Expr returnType, Expr leadingParameter, Expr trailingParameters)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "java/lang/invoke/MethodType",
                returnType,
                leadingParameter,
                trailingParameters);
    }

    private static void dropLeadingObjectArgument(AdvInsnBuilder ib, Local handle)
    {
        Local leadingObject = classArray(ib, "leadingObject", objectClass());
        ib.set(handle, AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodHandles",
                "dropArguments",
                "java/lang/invoke/MethodHandle",
                handle,
                AdvInsnBuilder.constant(0),
                leadingObject));
    }

    private static void validateBranches()
    {
        for (Opcs opcode : Opcs.values())
        {
            if(opcode == Opcs.INVOKEDYNAMIC || opcode == Opcs.SUPER_INSTRUCTION)
            {
                continue;
            }
            if (!branches.containsKey(opcode))
            {
                throw new IllegalStateException("No InterpretBranch for " + opcode);
            }
        }
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

    private record InterpretChunk(int index, List<Opcs> opcodes, MethodNode method)
    {
    }

    private record InterpretChunkSlot(int chunkIndex, int opcodeIndex)
    {
    }

    private record DispatchEntry(
            int key,
            int primaryKey,
            Opcs opcode,
            InterpretChunkSlot slot)
    {
    }

    private record SuperInstructionChunk(
            int index,
            List<SuperInstructionRegistry.Recipe> recipes,
            MethodNode method)
    {
    }
}
