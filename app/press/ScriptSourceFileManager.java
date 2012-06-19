package press;

public class ScriptSourceFileManager extends SourceFileManager {
    public ScriptSourceFileManager() {
        super("JavaScript", ScriptCompressor.EXTENSION, "#{press.script}",
                "#{press.compressed-script}", "<!-- press-js: ", " -->", PluginConfig.js.srcDir);
    }
}
