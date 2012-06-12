package press;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import play.Logger;
import play.vfs.VirtualFile;
import press.ScriptCompressor.PressErrorReporter;
import press.io.CompressedFile;
import press.io.FileIO;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;
import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class StyleCompressor extends Compressor {
    public static final String EXTENSION = ".css";

    public StyleCompressor() {
        super(PluginConfig.css.srcDir, PluginConfig.css.compressedDir, EXTENSION);
    }

    public static int clearCache() {
        return clearCache(PluginConfig.css.compressedDir, EXTENSION);
    }

    @Override
    public void compress(File sourceFile, Writer out) throws IOException {
        try {
            Reader in;
            // If it's a less file, run it through the less compiler first
            if (isLess(sourceFile.getName())) {
                LessEngine engine = new LessEngine();
                String css = engine.compile(sourceFile);
                in = new BufferedReader(new StringReader(css));
            } else {
                in = FileIO.getReader(sourceFile);
            }
            
            // Compress the CSS
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, PluginConfig.css.lineBreak);
        } catch (LessException e) {
            throw new IOException(e);
        }
    }

    public static boolean isLess(String fileName) {
        return fileName.toLowerCase().endsWith(".less");
    }
}
