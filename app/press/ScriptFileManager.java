package press;

public class ScriptFileManager extends SourceFileManager {
    public ScriptFileManager() {
        super("JavaScript", ScriptCompressor.EXTENSION, "#{press.script}",
                "#{press.compressed-script}", "<!-- press-js: ", " -->", PluginConfig.js.srcDir);
    }
}
