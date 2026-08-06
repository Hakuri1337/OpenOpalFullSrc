package wtf.oraculus.scripting.repository;

import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import wtf.oraculus.scripting.Script;
import wtf.oraculus.scripting.impl.ModuleScript;
import wtf.oraculus.scripting.impl.proxy.ClientProxy;
import wtf.oraculus.scripting.impl.proxy.MovementProxy;
import wtf.oraculus.scripting.impl.proxy.RenderProxy;
import wtf.oraculus.scripting.impl.proxy.RotationProxy;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static wtf.oraculus.client.Constants.DIRECTORY;
import static wtf.oraculus.client.Constants.mc;

public final class ScriptRepository {

    private final List<Script> scriptList = new ArrayList<>();

    public ScriptRepository() {
        loadScripts();
    }

    public int loadScripts() {
        scriptList.forEach(script -> {
            final ModuleScript module = script.getModule();
            if (module != null) {
                module.setEnabled(false);
            }
            script.getContext().close();
        });
        scriptList.clear();

        final File scriptsDir = new File(DIRECTORY, "scripts");
        final File[] jsFiles = scriptsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".js"));
        if (jsFiles == null) {
            return 0;
        }

        for (final File scriptFile : jsFiles) {
            final Context ctx = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(name -> true)
                    .allowIO(IOAccess.ALL)
                    .allowCreateProcess(false)
                    .allowCreateThread(true)
                    .allowNativeAccess(false)
                    .build();

            try (final Reader reader = new FileReader(scriptFile)) {
                ctx.getBindings("js").putMember("registerScript", (ProxyExecutable) args -> {
                    final Value data = args[0];
                    final String name = data.getMember("name").asString();
                    final String ver  = data.getMember("version").asString();

                    final List<String> authors = new ArrayList<>();
                    final Value arr = data.getMember("authors");
                    for (int i = 0; i < arr.getArraySize(); i++) {
                        authors.add(arr.getArrayElement(i).asString());
                    }

                    return new Script(name, ver, authors, ctx);
                });

                ctx.getBindings("js").putMember("client", new ClientProxy());
                ctx.getBindings("js").putMember("renderer", new RenderProxy());
                ctx.getBindings("js").putMember("movement", new MovementProxy());
                ctx.getBindings("js").putMember("rotation", new RotationProxy());

                ctx.getBindings("js").putMember("Vec3d", Vec3d.class);
                ctx.getBindings("js").putMember("Vec3i", Vec3i.class);
                ctx.getBindings("js").putMember("BlockPos", BlockPos.class);
                ctx.getBindings("js").putMember("MathHelper", MathHelper.class);
                ctx.getBindings("js").putMember("Hand", Hand.class);
                ctx.getBindings("js").putMember("mc", mc);

                final Source source = Source.newBuilder("js", reader, scriptFile.getName()).build();
                ctx.eval(source);

                final Value scriptValue = ctx.getBindings("js").getMember("script");
                if (scriptValue == null || scriptValue.isNull()) {
                    throw new IllegalStateException(
                            "Global 'script' was not defined in " + scriptFile.getName()
                    );
                }

                final Script script = scriptValue.asHostObject();
                scriptList.add(script);
            } catch (IOException e) {
                e.printStackTrace();
                ctx.close();
            }
        }
        return jsFiles.length;
    }

    public List<Script> getScriptList() {
        return scriptList;
    }
}
