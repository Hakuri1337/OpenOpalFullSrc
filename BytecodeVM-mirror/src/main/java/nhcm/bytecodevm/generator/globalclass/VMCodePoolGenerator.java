package nhcm.bytecodevm.generator.globalclass;

import lombok.Getter;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.abstracts.ClassObj;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.builder.MethodRef;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VMCodePoolGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    public final VMProgramGenerator vmProgramGenerator;
    public final MethodRef find;

    public VMCodePoolGenerator(String className, VMProgramGenerator vmProgramGenerator)
    {
        this(className, vmProgramGenerator, GeneratedMemberNamer.DISABLED);
    }

    public VMCodePoolGenerator(String className, VMProgramGenerator vmProgramGenerator, GeneratedMemberNamer namer)
    {
        super(className);
        this.vmProgramGenerator = vmProgramGenerator;
        this.find = new MethodRef(className, namer.method(className, "find", "(I)" + vmProgramGenerator.descriptor()), "(I)" + vmProgramGenerator.descriptor());

        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.INTERFACE, Acc.ABSTRACT}, className);
        MethodNode findMethod = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.ABSTRACT}, find.name(), find.descriptor());
        cn.methods.add(findMethod);
        this.classNode = cn;
    }
}
