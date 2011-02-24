package press;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import play.PlayPlugin;

public class Plugin extends PlayPlugin {
    static ThreadLocal<JSCompressor> jsCompressor = new ThreadLocal<JSCompressor>();
    static ThreadLocal<CSSCompressor> cssCompressor = new ThreadLocal<CSSCompressor>();
    static ThreadLocal<Boolean> errorOccurred = new ThreadLocal<Boolean>();
    static ThreadLocal<Map<String, Boolean>> jsFiles = new ThreadLocal<Map<String, Boolean>>();
    static ThreadLocal<Map<String, Boolean>> cssFiles = new ThreadLocal<Map<String, Boolean>>();

    @Override
    public void onApplicationStart() {
        // Read the config each time the application is restarted
        PluginConfig.readConfig();

        // Clear the cache
        JSCompressor.clearCache();
        CSSCompressor.clearCache();
    }

    @Override
    public void beforeActionInvocation(Method actionMethod) {
        // Before each action, reinitialize variables
        jsCompressor.set(new JSCompressor());
        cssCompressor.set(new CSSCompressor());
        errorOccurred.set(false);
        jsFiles.set(new HashMap<String, Boolean>());
        cssFiles.set(new HashMap<String, Boolean>());
    }

    /**
     * Add a single JS file to compression
     */
    public static String addSingleJS(String fileName) {
        checkJSFileExists(fileName);
        JSCompressor compressor = jsCompressor.get();
        String src = null;
        if (performCompression()) {
            src = compressor.compressedSingleFileUrl(fileName);
        } else {
            src = compressor.srcDir + fileName;
        }

        return getScriptTag(src);
    }

    /**
     * Add a single CSS file to compression
     */
    public static String addSingleCSS(String fileName) {
        checkCSSFileExists(fileName);
        CSSCompressor compressor = cssCompressor.get();
        String src = null;
        if (performCompression()) {
            src = compressor.compressedSingleFileUrl(fileName);
        } else {
            src = compressor.srcDir + fileName;
        }

        return getLinkTag(src);
    }

    /**
     * Adds the given source file(s) to the JS compressor, returning the file
     * signature to be output in HTML
     */
    public static String addJS(String src, boolean compress) {
        JSCompressor compressor = jsCompressor.get();
        String baseUrl = compressor.srcDir;
        String result = "";
        for (String fileName : PressFileGlobber.getResolvedFiles(src, baseUrl)) {
            checkForJSDuplicates(fileName);

            if (performCompression()) {
                result += compressor.add(fileName, compress) + "\n";
            } else {
                result += getScriptTag(baseUrl + fileName);
            }
        }

        return result;
    }

    /**
     * Adds the given source file(s) to the CSS compressor, returning the file
     * signature to be output in HTML
     */
    public static String addCSS(String src, boolean compress) {
        CSSCompressor compressor = cssCompressor.get();
        String baseUrl = compressor.srcDir;
        String result = "";
        for (String fileName : PressFileGlobber.getResolvedFiles(src, baseUrl)) {
            checkForCSSDuplicates(fileName);

            if (performCompression()) {
                result += compressor.add(fileName, compress) + "\n";
            } else {
                result += getLinkTag(baseUrl + fileName);
            }
        }

        return result;
    }

    /**
     * Outputs the tag indicating where the compressed CSS should be included.
     */
    public static String compressedCSSTag() {
        if (performCompression()) {
            return getLinkTag(cssCompressor.get().compressedUrl());
        }
        return "";
    }

    /**
     * Outputs the tag indicating where the compressed CSS should be included.
     */
    public static String compressedJSTag() {
        if (performCompression()) {
            return getScriptTag(jsCompressor.get().compressedUrl());
        }
        return "";
    }

    @Override
    public void afterActionInvocation() {
        // At the end of the action, save the list of files that will be
        // associated with this request
        if (jsCompressor.get() != null && cssCompressor.get() != null && !hasErrorOccurred()) {
            jsCompressor.get().saveFileList();
            cssCompressor.get().saveFileList();
        }
    }

    @Override
    public void onInvocationException(Throwable e) {
        errorOccurred.set(true);
    }

    /**
     * Indicates whether or not an error has occurred
     */
    public static boolean hasErrorOccurred() {
        return errorOccurred.get() == null || errorOccurred.get();
    }

    /**
     * Indicates whether or not to compress files
     */
    public static boolean performCompression() {
        return PluginConfig.enabled && !hasErrorOccurred();
    }

    /**
     * Check if the given JS file exists.
     */
    public static void checkJSFileExists(String fileName) {
        JSCompressor.checkJSFileExists(fileName);
    }

    /**
     * Check if the given CSS file exists.
     */
    public static void checkCSSFileExists(String fileName) {
        CSSCompressor.checkCSSFileExists(fileName);
    }

    /**
     * Check if the given JS file has already been included.
     */
    public static void checkForJSDuplicates(String fileName) {
        checkJSFileExists(fileName);
        checkForDuplicates(jsFiles.get(), fileName, JSCompressor.FILE_TYPE, JSCompressor.TAG_NAME);
    }

    /**
     * Check if the given CSS file has already been included.
     */
    public static void checkForCSSDuplicates(String fileName) {
        checkCSSFileExists(fileName);
        checkForDuplicates(cssFiles.get(), fileName, CSSCompressor.FILE_TYPE,
                CSSCompressor.TAG_NAME);
    }

    private static void checkForDuplicates(Map<String, Boolean> files, String fileName,
            String fileType, String tagName) {

        if (!files.containsKey(fileName)) {
            files.put(fileName, true);
            return;
        }

        throw new DuplicateFileException(fileType, fileName, tagName);
    }

    /**
     * Returns a script tag which can be used to output uncompressed JavaScript
     * tags within the HTML.
     */
    private static String getScriptTag(String src) {
        return "<script src=\"" + src
                + "\" type=\"text/javascript\" language=\"javascript\" charset=\"utf-8\">"
                + "</script>\n";
    }

    /**
     * Returns a link tag which can be used to output uncompressed CSS tags
     * within the HTML.
     */
    private static String getLinkTag(String src) {
        return "<link href=\"" + src + "\" rel=\"stylesheet\" type=\"text/css\" charset=\"utf-8\">"
                + (press.PluginConfig.htmlCompatible ? "" : "</link>") + "\n";
    }
}
