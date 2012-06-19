package press;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import play.PlayPlugin;
import play.libs.Crypto;
import play.templates.JavaExtensions;
import press.io.CompressedFile;
import press.io.FileIO;

public abstract class Compressor {
    /**
     * A key unique for the list of component files and their last modified date
     */
    abstract public String getCompressedFileKey(List<FileInfo> componentFiles);

    abstract public void compress(File file, Writer out, boolean compress) throws IOException;
    
    protected static int clearCache(String compressedDir, String extension) {
        return CompressedFile.clearCache(compressedDir, extension);
    }
}
