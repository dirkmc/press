package press;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import play.Logger;
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
     * Get the url for the compressed version of the given JS file, in real time
     */
    public static String compressedSingleJSUrl(String fileName) {
        return jsCompressor.get().compressedSingleFileUrl(fileName);
    }

    /**
     * Get the url for the compressed version of the given CSS file, in real
     * time
     */
    public static String compressedSingleCSSUrl(String fileName) {
        return cssCompressor.get().compressedSingleFileUrl(fileName);
    }

    public static boolean outputJSTag(String fileName, boolean compress, boolean ignoreDuplicates) {
        return outputTag(jsFiles.get(), fileName, compress, ignoreDuplicates,
                JSCompressor.FILE_TYPE, JSCompressor.TAG_NAME);
    }

    public static boolean outputCSSTag(String fileName, boolean compress, boolean ignoreDuplicates) {
        return outputTag(cssFiles.get(), fileName, compress, ignoreDuplicates,
                CSSCompressor.FILE_TYPE, CSSCompressor.TAG_NAME);
    }

    /**
     * If the file is included multiple times, and ignoreDuplicates is true, we
     * only want to output the <script> or <link rel="css"> tag the first time.
     */
    private static boolean outputTag(Map<String, Boolean> files, String fileName, boolean compress,
            boolean ignoreDuplicates, String fileType, String tagName) {
        
        if (!files.containsKey(fileName)) {
            files.put(fileName, true);
            return true;
        }

        if (ignoreDuplicates) {
            PressLogger.trace("Ignoring duplicate file %s", fileName);
            return false;
        }

        throw new DuplicateFileException(fileType, fileName, tagName);
    }

    /**
     * Adds the given file to the JS compressor, returning the file signature to
     * be output in HTML
     */
    public static String addJS(String fileName, boolean compress, boolean ignoreDuplicates) {
        return jsCompressor.get().add(fileName, compress, ignoreDuplicates);
    }

    /**
     * Adds the given file to the CSS compressor, returning the file signature
     * to be output in HTML
     */
    public static String addCSS(String fileName, boolean compress, boolean ignoreDuplicates) {
        return cssCompressor.get().add(fileName, compress, ignoreDuplicates);
    }

    /**
     * Called when the template outputs the tag indicating where the compressed
     * javascript should be included. This method returns the URL of the
     * compressed file.
     */
    public static String compressedJSUrl() {
        return jsCompressor.get().compressedUrl();
    }

    /**
     * Called when the template outputs the tag indicating where the compressed
     * CSS should be included. This method returns the URL of the compressed
     * file.
     */
    public static String compressedCSSUrl() {
        return cssCompressor.get().compressedUrl();
    }

    @Override
    public void afterActionInvocation() {
        // At the end of the action, save the list of files that will be
        // associated with this request
        if (jsCompressor.get() != null && cssCompressor.get() != null) {
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
     * Indicates whether or not compression is enabled.
     */
    public static boolean enabled() {
        return PluginConfig.enabled;
    }

    /**
     * Indicates whether or not to compress files
     */
    public static boolean performCompression() {
        return enabled() && !hasErrorOccurred();
    }
}
