package controllers.press;

import java.io.File;
import java.util.List;

import play.mvc.Controller;
import play.vfs.VirtualFile;
import press.CSSCompressor;
import press.JSCompressor;
import press.PluginConfig;

public class Press extends Controller {

    public static void getCompressedJS(String key) {
        VirtualFile compressedFile = JSCompressor.getCompressedFile(key);
        if (compressedFile == null) {
            notFound();
        }

        renderBinary(compressedFile.getRealFile());
    }

    public static void getCompressedCSS(String key) {
        VirtualFile compressedFile = CSSCompressor.getCompressedFile(key);
        if (compressedFile == null) {
            notFound();
        }

        renderBinary(compressedFile.getRealFile());
    }

    public static void clearJSCache() {
        if (!PluginConfig.cacheClearEnabled) {
            forbidden();
        }

        List<File> files = JSCompressor.clearCache();
        renderText("Cleared " + files.size() + " files from cache");
    }

    public static void clearCSSCache() {
        if (!PluginConfig.cacheClearEnabled) {
            forbidden();
        }

        List<File> files = CSSCompressor.clearCache();
        renderText("Cleared " + files.size() + " files from cache");
    }
}