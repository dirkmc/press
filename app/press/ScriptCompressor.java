package press;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import play.Logger;
import play.vfs.VirtualFile;
import press.io.CompressedFile;
import press.io.FileIO;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class ScriptCompressor extends Compressor {
    public static final String EXTENSION = ".js";

    public static int clearCache() {
        return clearCache(PluginConfig.js.compressedDir, EXTENSION);
    }

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
    }

    @Override
    public void compress(File sourceFile, Writer out, boolean compress) throws IOException {
        if (!compress) {
            FileIO.write(FileIO.getReader(sourceFile), out);
            return;
        }

        ErrorReporter errorReporter = new PressErrorReporter(sourceFile.getName());
        Reader in = FileIO.getReader(sourceFile);
        JavaScriptCompressor compressor = new JavaScriptCompressor(in, errorReporter);
        compressor.compress(out, PluginConfig.js.lineBreak, PluginConfig.js.munge,
                PluginConfig.js.warn, PluginConfig.js.preserveAllSemiColons,
                PluginConfig.js.preserveStringLiterals);
    }

    @Override
    public String getCompressedFileKey(List<FileInfo> componentFiles) {
        Map<String, Long> files = FileInfo.getFileLastModifieds(componentFiles);
        return CacheManager.getCompressedFileKey(files, EXTENSION);
    }
}
