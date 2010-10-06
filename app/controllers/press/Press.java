package controllers.press;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;

import play.exceptions.UnexpectedException;
import play.mvc.Controller;
import play.vfs.VirtualFile;
import press.CSSCompressor;
import press.JSCompressor;
import press.PluginConfig;

public class Press extends Controller {

    public static void getCompressedJS(String key) {
        VirtualFile compressedFile = JSCompressor.getCompressedFile(key);
        if (compressedFile == null) {
            renderBadResponse("JavaScript");
        }

        renderBinary(compressedFile.getRealFile());
    }

    public static void getCompressedCSS(String key) {
        VirtualFile compressedFile = CSSCompressor.getCompressedFile(key);
        if (compressedFile == null) {
            renderBadResponse("CSS");
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

    private static void renderBadResponse(String fileType) {
        String response = "/*\n";
        response += "The compressed " + fileType + " file could not be generated.\n";
        response += "This can occur in two situations:\n";
        response += "1. The time between when the page was rendered by the ";
        response += "server and when the browser requested the compressed ";
        response += "file was greater than the timeout. (The timeout is ";
        response += "currently configured to be ";
        response += PluginConfig.compressionKeyStorageTime + ")\n";
        response += "2. There was an exception thrown while rendering the ";
        response += "page. Note that the press plugin compression will not ";
        response += "work in error pages (eg 404.html or 500.html).\n";
        response += "*/";
        renderBinaryResponse(response);
    }

    private static void renderBinaryResponse(String response) {
        try {
            renderBinary(new ByteArrayInputStream(response.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new UnexpectedException(e);
        }
    }
}