package press;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import play.vfs.VirtualFile;

public class FileInfo implements Serializable {
    boolean compress;
    public File file;

    public FileInfo(boolean compress, VirtualFile file) {
        this.compress = compress;
        // We store the File instead of a VirtualFile so that this class can be
        // serialized
        this.file = file == null ? null : file.getRealFile();
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public static List<File> getFiles(List<FileInfo> fileInfos) {
        List<File> files = new ArrayList<File>();
        for (FileInfo info : fileInfos) {
            files.add(info.file);
        }
        return files;
    }

    public static Map<String, Long> getFileLastModifieds(List<FileInfo> fileInfos) {
        Map<String, Long> files = new HashMap<String, Long>();
        for (FileInfo info : fileInfos) {
            files.put(info.file.getAbsolutePath(), info.file.lastModified());
        }
        return files;
    }
}
