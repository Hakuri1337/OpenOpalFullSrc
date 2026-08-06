package nhcm.bytecodevm.config.sdk;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.List;

/** Removes BytecodeVM SDK declaration annotations from transformed classes. */
public final class SdkAnnotationRemover
{
    private SdkAnnotationRemover()
    {
    }

    public static int remove(Collection<ClassNode> classes)
    {
        int removed = 0;
        for (ClassNode owner : classes)
        {
            removed += remove(owner.visibleAnnotations);
            removed += remove(owner.invisibleAnnotations);
            for (FieldNode field : owner.fields)
            {
                removed += remove(field.visibleAnnotations);
                removed += remove(field.invisibleAnnotations);
            }
            for (MethodNode method : owner.methods)
            {
                removed += remove(method.visibleAnnotations);
                removed += remove(method.invisibleAnnotations);
            }
        }
        return removed;
    }

    private static int remove(List<AnnotationNode> annotations)
    {
        if (annotations == null)
        {
            return 0;
        }
        int before = annotations.size();
        annotations.removeIf(annotation -> annotation.desc.startsWith(SdkAnnotationReader.SDK_PREFIX));
        return before - annotations.size();
    }
}
