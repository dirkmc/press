package press;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import play.vfs.VirtualFile;

public class FileInfo implements Serializable {
    String fileName;
    boolean compress;
    public File file;

    public FileInfo(String fileName, boolean compress, VirtualFile file) {
        this.fileName = fileName;
        this.compress = compress;
        this.file = file == null ? null : file.getRealFile();
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public static Collection<String> getFileNames(List<FileInfo> list) {
        Collection<String> fileNames = new ArrayList<String>(list.size());
        for (FileInfo fileInfo : list) {
            fileNames.add(fileInfo.fileName);
        }

        return fileNames;
    }

    public static Collection<String> getFileNamesAndModifiedTimestamps(List<FileInfo> list) {
        Collection<String> fileNamesAndModifiedTimestamps = new ArrayList<String>(list.size());
        for (FileInfo fileInfo : list) {
            fileNamesAndModifiedTimestamps.add(fileInfo.fileName + fileInfo.getLastModified());
        }

        return fileNamesAndModifiedTimestamps;
    }
}
