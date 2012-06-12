package press;

public class ScriptRequestHandler extends RequestHandler {
    private SourceFileManager srcManager = new ScriptFileManager();
    private Compressor compressor = new ScriptCompressor();

    @Override
    public String getSingleCompressedUrl(String requestKey) {
        return getCompressedUrl("press.Press.getSingleCompressedJS", requestKey);
    }

    @Override
    public String getMultiCompressedUrl(String requestKey) {
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
    protected Compressor getCompressor() {
        return compressor;
    }

    public static int clearCache() {
        return ScriptCompressor.clearCache();
    }
}
