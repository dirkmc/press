package press;

public class ScriptCompressedFileManager extends CompressedFileManager {
    public ScriptCompressedFileManager() {
        super(new ScriptCompressor());
    }

    public String getCompressedDir() {
        return PluginConfig.js.compressedDir;
    }
}
