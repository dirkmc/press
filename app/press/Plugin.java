package press;

import java.lang.reflect.Method;

import play.PlayPlugin;

public class Plugin extends PlayPlugin {
    static ThreadLocal<JSCompressor> jsCompressor = new ThreadLocal<JSCompressor>();
    static ThreadLocal<CSSCompressor> cssCompressor = new ThreadLocal<CSSCompressor>();
    
    /**
     * Called by play to ask the plugin if anything has changed. We check to see
     * if the configuration file has changed, and if so we throw an exception,
     * which tells play to restart. Play will then call onApplicationStart().
     */
    @Override
    public void detectChange() {
        // If the configuration has changed, we throw an exception, which tells
        // play to restart
        if (PluginConfig.hasChanged()) {
            throw new RuntimeException();
        }
    }

    @Override
    public void onApplicationStart() {
        // Read the config each time the application is restarted
        PluginConfig.readConfig();
    }

    @Override
    public void beforeActionInvocation(Method actionMethod) {
        // Before each action, create a new CSS and JS Compressor
        jsCompressor.set(new JSCompressor());
        cssCompressor.set(new CSSCompressor());
    }

    public static void addJS(String fileName) {
        // Add files to the JS compressor
        jsCompressor.get().add(fileName);
    }

    public static void addCSS(String fileName) {
        // Add files to the CSS compressor
        cssCompressor.get().add(fileName);
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
        jsCompressor.get().saveFileList();
        cssCompressor.get().saveFileList();
    }

    /**
     * Indicates whether or not compression is enabled.
     */
    public static boolean enabled() {
        return PluginConfig.enabled;
    }
}
