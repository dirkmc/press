package press;

public class StyleRequestHandler extends RequestHandler {
    private SourceFileManager srcManager = new StyleFileManager();
    private Compressor compressor = new StyleCompressor();

    @Override
    public String getSingleCompressedUrl(String requestKey) {
        return getCompressedUrl("press.Press.getSingleCompressedCSS", requestKey);
    }

    @Override
    public String getMultiCompressedUrl(String requestKey) {
        return getCompressedUrl("press.Press.getCompressedCSS", requestKey);
    }

    @Override
    public String getTag(String src) {
        return "<link href=\"" + press.PluginConfig.contentHostingDomain + src
                + "\" rel=\"stylesheet\" type=\"text/css\" charset=\"utf-8\">"
                + (press.PluginConfig.htmlCompatible ? "" : "</link>") + "\n";
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
        return StyleCompressor.clearCache();
    }
}
