package press;

public class ScriptCompressedFileManager extends CompressedFileManager {
    public ScriptCompressedFileManager() {
        super(new ScriptCompressor());
    }
}
