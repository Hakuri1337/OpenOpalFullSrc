package nhcm.bytecodevm.advInsn;

import nhcm.bytecodevm.utils.builder.InsnBuilder;
import nhcm.bytecodevm.utils.TypeUtils;
import org.objectweb.asm.Type;

public record Local(String name, Type type, int index) implements Expr
{
    @Override
    public void emit(InsnBuilder builder)
    {
        TypeUtils.load(builder, type, index);
    }

    @Override
    public String source()
    {
        return name;
    }
}
