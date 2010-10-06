package press;

import java.lang.reflect.Method;

import play.PlayPlugin;

public class Plugin extends PlayPlugin {
    static ThreadLocal<JSCompressor> jsCompressor = new ThreadLocal<JSCompressor>();
    static ThreadLocal<CSSCompressor> cssCompressor = new ThreadLocal<CSSCompressor>();

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
        // Before each action, create a new CSS and JS Compressor
        jsCompressor.set(new JSCompressor());
        cssCompressor.set(new CSSCompressor());
    }

    /**
     * Get the url for the compressed version of the given JS file, in real time
     */
    public static String compressedSingleJSUrl(String fileName) {
        // This can happen in some extreme error cases
        if(jsCompressor.get() == null) {
            return "";
        }
        
        return jsCompressor.get().compressedSingleFileUrl(fileName);
    }

    /**
     * Get the url for the compressed version of the given CSS file, in real time
     */
    public static String compressedSingleCSSUrl(String fileName) {
        // This can happen in some extreme error cases
        if(cssCompressor.get() == null) {
            return "";
        }
        
        return cssCompressor.get().compressedSingleFileUrl(fileName);
    }

    public static String addJS(String fileName, boolean compress) {
        // This can happen in some extreme error cases
        if(jsCompressor.get() == null) {
            return "";
        }
        
        // Add files to the JS compressor
        return jsCompressor.get().add(fileName, compress);
    }

    public static String addCSS(String fileName, boolean compress) {
        // This can happen in some extreme error cases
        if(cssCompressor.get() == null) {
            return "";
        }
        
        // Add files to the CSS compressor
        return cssCompressor.get().add(fileName, compress);
    }

    /**
     * Called when the template outputs the tag indicating where the compressed
     * javascript should be included. This method returns the URL of the
     * compressed file.
     */
    public static String compressedJSUrl() {
        // This can happen in some extreme error cases
        if(jsCompressor.get() == null) {
            return "";
        }
        
        return jsCompressor.get().compressedUrl();
    }

    /**
     * Called when the template outputs the tag indicating where the compressed
     * CSS should be included. This method returns the URL of the compressed
     * file.
     */
    public static String compressedCSSUrl() {
        // This can happen in some extreme error cases
        if(cssCompressor.get() == null) {
            return "";
        }
        
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

    /**
     * Indicates whether or not compression is enabled.
     */
    public static boolean enabled() {
        return PluginConfig.enabled;
    }
}
