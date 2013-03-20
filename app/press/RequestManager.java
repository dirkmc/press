package press;

import press.io.PressFileGlobber;

/**
 * Manages the state of a single request to render the page
 */
public class RequestManager {
    public static final boolean RQ_TYPE_SCRIPT = true;
    public static final boolean RQ_TYPE_STYLE = !RQ_TYPE_SCRIPT;

    private boolean errorOccurred = false;
    private RequestHandler scriptRequestHandler = new ScriptRequestHandler();
    private RequestHandler styleRequestHandler = new StyleRequestHandler();

    private RequestHandler getRequestHandler(boolean rqType) {
        return rqType == RQ_TYPE_SCRIPT ? scriptRequestHandler : styleRequestHandler;
    }

    public String addSingleFile(boolean rqType, String fileName) {
        RequestHandler handler = getRequestHandler(rqType);
        handler.checkFileExists(fileName);

        String src = null;
        if (performCompression()) {
            src = handler.getCompressedUrl(handler.getSingleFileCompressionKey(fileName));
        } else {
            src = handler.getSrcDir() + fileName;
        }

        return handler.getTag(src);
    }

    public String addMultiFile(boolean rqType, String src, boolean packFile) {
        RequestHandler handler = getRequestHandler(rqType);
        String baseUrl = handler.getSrcDir();
        String result = "";
        for (String fileName : PressFileGlobber.getResolvedFiles(src, baseUrl)) {
            handler.checkFileExists(fileName);
            handler.checkForDuplicates(fileName);

            if (performCompression()) {
                result += handler.add(fileName, packFile) + "\n";
            } else {
                result += handler.getTag(baseUrl + fileName);
            }
        }

        return result;
    }

    public String compressedTag(boolean rqType) {
        RequestHandler handler = getRequestHandler(rqType);
        if (performCompression()) {
            String requestKey = handler.closeRequest();
            return handler.getTag(handler.getCompressedUrl(requestKey));
        }
        return "";
    }

    public void saveFileList() {
        if (!performCompression()) {
            return;
        }

        scriptRequestHandler.saveFileList();
        styleRequestHandler.saveFileList();
    }

    public void errorOccurred() {
        errorOccurred = true;
    }

    private boolean performCompression() {
        return PluginConfig.enabled && !errorOccurred;
    }

    public static void clearCache() {
        ScriptRequestHandler.clearCache();
        StyleRequestHandler.clearCache();
    }
}
