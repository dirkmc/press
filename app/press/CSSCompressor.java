package press;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import play.vfs.VirtualFile;

import com.yahoo.platform.yui.compressor.CssCompressor;

public class CSSCompressor extends Compressor {
    private static final String EXTENSION = ".css";

    public CSSCompressor() {
        super("CSS", EXTENSION, "press.Press.getCompressedCSS", "#{press.stylesheet}",
                "#{press.compressed-stylesheet}", "<!-- press-css: ", " -->",
                PluginConfig.css.srcDir, PluginConfig.css.compressedDir);
    }

    public String compressedSingleFileUrl(String fileName) {
        return compressedSingleFileUrl(cssFileCompressor, fileName);
    }

    public static VirtualFile getCompressedFile(String key) {
        return getCompressedFile(cssFileCompressor, key, PluginConfig.css.compressedDir, EXTENSION);
    }

    public static List<File> clearCache() {
        return clearCache(PluginConfig.css.compressedDir, EXTENSION);
    }

    static FileCompressor cssFileCompressor = new FileCompressor() {
        public void compress(String fileName, Reader in, Writer out) throws IOException {
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, PluginConfig.css.lineBreak);
        }
    };
}
