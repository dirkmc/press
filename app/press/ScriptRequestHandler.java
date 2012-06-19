package press;

public class ScriptRequestHandler extends RequestHandler {
    private SourceFileManager srcManager = new ScriptSourceFileManager();
    private CompressedFileManager compressManager = new ScriptCompressedFileManager();

    @Override
    public String getCompressedUrl(String requestKey) {
        return getCompressedUrl("press.Press.getCompressedJS", requestKey);
    }

    @Override
    public String getTag(String src) {
        return "<script src=\"" + press.PluginConfig.contentHostingDomain + src
                + "\" type=\"text/javascript\" language=\"javascript\" charset=\"utf-8\">"
                + "</script>\n";
    }

    @Override
    protected SourceFileManager getSourceManager() {
        return srcManager;
    }

    @Override
    protected CompressedFileManager getCompressedFileManager() {
        return compressManager;
    }

    public static int clearCache() {
        return ScriptCompressor.clearCache();
    }

    @Override
    public String getSingleFileCompressionKey(String fileName) {
        return super.getSingleFileCompressionKey(fileName, new ScriptSourceFileManager());
    }
}
