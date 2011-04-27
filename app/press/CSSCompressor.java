package press;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import play.vfs.VirtualFile;
import press.io.CompressedFile;
import press.io.FileIO;

import com.yahoo.platform.yui.compressor.CssCompressor;

public class CSSCompressor extends Compressor {
    public static final String TAG_NAME = "#{press.stylesheet}";
    public static final String FILE_TYPE = "CSS";
    public static final String EXTENSION = ".css";

    public CSSCompressor() {
        super(FILE_TYPE, EXTENSION, TAG_NAME, "#{press.compressed-stylesheet}", "<!-- press-css: ",
                " -->", PluginConfig.css.srcDir, PluginConfig.css.compressedDir);
    }

    public String compressedSingleFileUrl(String fileName) {
        return compressedSingleFileUrl(cssFileCompressor, fileName);
    }

    public static CompressedFile getCompressedFile(String key) {
        return getCompressedFile(cssFileCompressor, key, PluginConfig.css.compressedDir, EXTENSION);
    }

    public static VirtualFile checkCSSFileExists(String fileName) {
        return FileIO.checkFileExists(fileName, PluginConfig.css.srcDir);
    }

    public static int clearCache() {
        return clearCache(PluginConfig.css.compressedDir, EXTENSION);
    }

    static FileCompressor cssFileCompressor = new FileCompressor() {
        public void compress(String fileName, Reader in, Writer out) throws IOException {
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, PluginConfig.css.lineBreak);
        }
    };
}
