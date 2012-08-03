package press.io;

import java.io.InputStream;
import java.io.Writer;

import press.PluginConfig;

public abstract class CompressedFile {
    private String fileKey;

    protected CompressedFile(String fileKey) {
        this.fileKey = fileKey;
    }

    public String getFileKey() {
        return fileKey;
    }

    public static CompressedFile create(String fileKey, String compressedDir) {
        if (PluginConfig.isInMemoryStorage()) {
            return new InMemoryCompressedFile(fileKey);
        }

        return new OnDiskCompressedFile(fileKey, compressedDir);
    }

    public static int clearCache(String compressedDir, String extension) {
        if (PluginConfig.isInMemoryStorage()) {
            return InMemoryCompressedFile.clearMemoryCache(extension);
        }

        return OnDiskCompressedFile.clearFileCache(compressedDir, extension);
    }
    
    public abstract InputStream inputStream();

    public abstract String name();

    public abstract boolean exists();

    public abstract Writer startWrite();

    public abstract void close();

    public abstract long length();
}
