package press;

import java.util.ArrayList;
import java.util.List;

import play.vfs.VirtualFile;
import press.io.CompressedFile;

public abstract class CompressedFileManager {
    private PressFileWriter pressFileWriter;
    private Compressor compressor;

    public CompressedFileManager(Compressor compressor) {
        this.compressor = compressor;
        this.pressFileWriter = new PressFileWriter(compressor);
    }

    /**
     * Get the compressed file with the given compression key
     */
    public CompressedFile getCompressedFile(String key) {
        List<FileInfo> componentFiles = SourceFileManager.getSourceFiles(key);

        // If there was nothing found for the given request key, return null.
        // This shouldn't happen unless there was a very long delay between the
        // template being rendered and the compressed file being requested
        if (componentFiles == null) {
            return null;
        }

        return getCompressedFile(componentFiles);
    }

    /**
     * Get the compressed file for the given set of component files
     */
    public CompressedFile getCompressedFile(List<FileInfo> componentFiles) {
        // First check if the compressor has a cached copy of the file
        String key = compressor.getCompressedFileKey(componentFiles);
        CompressedFile file = CompressedFile.create(key, getCompressedDir());
        if (CacheManager.useCachedFile(file)) {
            return file;
        }

        // If there is no cached file, generate one
        return pressFileWriter.writeCompressedFile(componentFiles, file);
    }
    
    public abstract String getCompressedDir();
}
