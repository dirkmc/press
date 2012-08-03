package press;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import press.io.CompressedFile;
import press.io.FileIO;
import com.yahoo.platform.yui.compressor.CssCompressor;

public class StyleCompressor extends Compressor {
    public static final String EXTENSION = ".css";
    public static final PlayLessEngine lessEngine = new PlayLessEngine();

    public static int clearCache() {
        return clearCache(PluginConfig.css.compressedDir, EXTENSION);
    }

    @Override
    public void compress(File sourceFile, Writer out, boolean compress) throws IOException {
        Reader in;

        // If it's a less file, use the less engine
        if (isLess(sourceFile.getName())) {
            // Note that the compress parameter doesn't actually seem to do
            // much here, not sure why
            String css = lessEngine.get(sourceFile, compress);
            in = new StringReader(css);
        } else {
            in = FileIO.getReader(sourceFile);
        }

        if (compress) {
            // Compress the CSS
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, PluginConfig.css.lineBreak);
        } else {
            // If the file should not be compressed, just copy it
            FileIO.write(in, out);
        }
    }

    public static boolean isLess(String fileName) {
        return fileName.toLowerCase().endsWith(".less");
    }

    @Override
    public String getCompressedFileKey(List<FileInfo> componentFiles) {
        Map<String, Long> files = FileInfo.getFileLastModifieds(componentFiles);

        // For each less file, set the last modified date for the less file to
        // the latest modified of all files that it imports
        for (String filePath : files.keySet()) {
            if (isLess(filePath)) {
                File lessFile = new File(filePath);
                if (lessFile.exists()) {
                    files.put(filePath, PlayLessEngine.latestModified(lessFile));
                }
            }
        }

        return CacheManager.getCompressedFileKey(files, EXTENSION);
    }
}
