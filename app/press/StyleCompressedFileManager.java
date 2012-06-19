package press;

public class StyleCompressedFileManager extends CompressedFileManager {
    public StyleCompressedFileManager() {
        super(new StyleCompressor());
    }
}
