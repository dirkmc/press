package press.io;

import java.io.File;
import java.io.FileFilter;

import play.vfs.VirtualFile;
import press.Compressor;

public class PressFileFilter implements FileFilter {
    String extension;

    public PressFileFilter(String extension) {
        this.extension = extension;
    }

    public boolean accept(File file) {
        if (!file.getName().endsWith(extension)) {
            return false;
        }

        // If the file contains a compression header, it's a press
        // compressed file
        VirtualFile virt = VirtualFile.open(file);
        CompressedFile compressedFile = CompressedFile.create(virt.relativePath());
        return compressedFile.exists() && Compressor.extractHeaderContent(compressedFile) != null;
    }
}
