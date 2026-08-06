package wtf.oraculus.scripting;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;
import wtf.oraculus.scripting.impl.ModuleScript;

import java.util.List;

public class Script {

    private final String name;
    private final String version;
    private final List<String> authors;

    private ModuleScript module;

    private final Context context;

    public Script(String name, String version, List<String> authors, Context context) {
        this.name = name;
        this.version = version;
        this.authors = authors;
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void registerModule(Value config, Value callback) {
        final String name = config.getMember("name").asString();
        final String description = config.getMember("description").asString();

        this.module = new ModuleScript(name, description);

        callback.execute(this.module);
    }

    @Nullable
    public ModuleScript getModule() {
        return module;
    }
}
