package press;

import java.lang.reflect.Method;

import play.PlayPlugin;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.vfs.VirtualFile;

public class Plugin extends PlayPlugin {
    static ThreadLocal<RequestManager> rqManager = new ThreadLocal<RequestManager>();
    static StaticAssetManager assetManager;

    @Override
    public void onApplicationStart() {
        // Read the config each time the application is restarted
        PluginConfig.readConfig();

        // Clear the asset cache
        RequestManager.clearCache();
        
        // Recreate the asset manager
        assetManager = new StaticAssetManager();
    }

    @Override
    public void beforeActionInvocation(Method actionMethod) {
        // Before each action, reinitialize variables
        rqManager.set(new RequestManager());
    }

    @Override
    public boolean serveStatic(VirtualFile file, Request request, Response response) {
        return assetManager.serveStatic(file, request, response);
    }

    /**
     * Add a single JS file to compression
     */
    public static String addSingleJS(String fileName) {
        return rqManager.get().addSingleFile(RequestManager.RQ_TYPE_SCRIPT, fileName);
    }

    /**
     * Add a single CSS file to compression
     */
    public static String addSingleCSS(String fileName) {
        return rqManager.get().addSingleFile(RequestManager.RQ_TYPE_STYLE, fileName);
    }

    /**
     * Adds the given source file(s) to the JS compressor, returning the file
     * signature to be output in HTML
     */
    public static String addJS(String src, boolean packFile) {
        return rqManager.get().addMultiFile(RequestManager.RQ_TYPE_SCRIPT, src, packFile);
    }

    /**
     * Adds the given source file(s) to the CSS compressor, returning the file
     * signature to be output in HTML
     */
    public static String addCSS(String src, boolean packFile) {
        return rqManager.get().addMultiFile(RequestManager.RQ_TYPE_STYLE, src, packFile);
    }

    /**
     * Outputs the tag indicating where the compressed JS should be included.
     */
    public static String compressedJSTag() {
        return rqManager.get().compressedTag(RequestManager.RQ_TYPE_SCRIPT);
    }

    /**
     * Outputs the tag indicating where the compressed CSS should be included.
     */
    public static String compressedCSSTag() {
        return rqManager.get().compressedTag(RequestManager.RQ_TYPE_STYLE);
    }

    @Override
    public void afterActionInvocation() {
        // When the action finishes, save the list of files added for
        // compression
        if (rqManager.get() != null) {
            rqManager.get().saveFileList();
        }

        rqManager.set(null);
    }

    @Override
    public void onInvocationException(Throwable e) {
        if (rqManager.get() != null) {
            rqManager.get().errorOccurred();
        }
    }
}
