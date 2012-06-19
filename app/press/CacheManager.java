package press;

import java.io.File;
import java.util.List;
import java.util.Map;

import play.libs.Crypto;
import play.templates.JavaExtensions;
import press.io.CompressedFile;
import press.io.FileIO;

public class CacheManager {

    public static boolean useCachedFile(CompressedFile file) {
        String filePath = file.getFileKey();
        if (file.exists() && useCache()) {
            PressLogger.trace("Using existing compressed file %s", filePath);
            return true;
        }

        if (!file.exists()) {
            PressLogger.trace("Compressed file %s does not yet exist", filePath);
        }
        PressLogger.trace("Generating compressed file %s", filePath);
        return false;
    }

    private static boolean useCache() {
        PressLogger.trace("Caching strategy is %s", PluginConfig.cache);
        if (PluginConfig.cache.equals(CachingStrategy.Never)) {
            return false;
        }

        // If the caching strategy is Change, we can still use the cache,
        // because we included the modification timestamp in the key name, so
        // same key means that it is not modified.
        return true;
    }

    /**
     * The key is a hash of each component file's path and last modified timestamp
     */
    public static String getCompressedFileKey(Map<String, Long> files, String extension) {
        // [myfile.css, another.less] ->
        // /path/to/compressed/dir/hashoffilenames.css
        StringBuffer key = new StringBuffer();
        for (String filePath : files.keySet()) {
            key.append(filePath);
            key.append(files.get(filePath));
        }
        return FileIO.lettersOnly(Crypto.passwordHash(key.toString())) + extension;
    }
}
