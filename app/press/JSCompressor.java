package press;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import play.Logger;
import play.vfs.VirtualFile;
import press.PluginConfig.js;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class JSCompressor extends Compressor {
    private static final String EXTENSION = ".js";

    public JSCompressor() {
        super(EXTENSION, "press.Press.getCompressedJS", "#{press.script}",
                "#{press.compressed-script}", js.srcDir, js.compressedDir);
    }

    public static VirtualFile getCompressedFile(String key) {
        return getCompressedFile(jsFileCompressor, key, js.compressedDir, EXTENSION);
    }

    public static List<File> clearCache() {
        return clearCache(js.compressedDir, EXTENSION);
    }

    static FileCompressor jsFileCompressor = new FileCompressor() {
        public void compress(String fileName, Reader in, Writer out) throws IOException {
            ErrorReporter errorReporter = new PressErrorReporter(fileName);
            JavaScriptCompressor compressor = new JavaScriptCompressor(in, errorReporter);
            compressor.compress(out, PluginConfig.js.lineBreak, PluginConfig.js.munge,
                    PluginConfig.js.warn, PluginConfig.js.preserveAllSemiColons,
                    PluginConfig.js.preserveStringLiterals);
        }
    };

    static class PressErrorReporter implements ErrorReporter {
        private static final String PREFIX = "[YUI Compressor] ";
        private static final String FORMAT_STRING = "%s:%d (char %d) %s";
        String fileName;

        public PressErrorReporter(String fileName) {
            this.fileName = fileName;
        }

        public void warning(String message, String sourceName, int line, String lineSource,
                int lineOffset) {
            if (line < 0 || (line == 1 && lineOffset == 0)) {
                Logger.warn(PREFIX + message);
            } else {
                Logger.warn(PREFIX + FORMAT_STRING, fileName, line, lineOffset, message);
            }
        }

        public void error(String message, String sourceName, int line, String lineSource,
                int lineOffset) {
            if (line < 0 || (line == 1 && lineOffset == 0)) {
                Logger.error(PREFIX + message);
            } else {
                Logger.error(PREFIX + FORMAT_STRING, fileName, line, lineOffset, message);
            }
        }

        public EvaluatorException runtimeError(String message, String sourceName, int line,
                String lineSource, int lineOffset) {
            error(message, sourceName, line, lineSource, lineOffset);
            return new EvaluatorException(message);
        }
    };
}
