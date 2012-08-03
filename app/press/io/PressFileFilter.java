package press.io;

import java.io.File;
import java.io.FileFilter;

import play.vfs.VirtualFile;
import press.PressFileWriter;

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
        return PressFileWriter.hasPressHeader(file);
    }
}
