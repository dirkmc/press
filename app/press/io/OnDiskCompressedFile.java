package press.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;
import press.PluginConfig;
import press.PressException;
import press.PressLogger;

public class OnDiskCompressedFile extends CompressedFile {
    private Writer writer;
    private VirtualFile file;
    private File tmpOutputFile;

    public OnDiskCompressedFile(String filePath, String compressedDir) {
        super(filePath);
        file = FileIO.getVirtualFile(compressedDir + filePath);
    }

    @Override
    public InputStream inputStream() {
        if (!exists()) {
            throw new PressException("Can't create InputStream. File does not exist");
        }

        return file.inputstream();
    }

    @Override
    public String name() {
        return file.getName();
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public Writer startWrite() {
        if (writer != null) {
            return writer;
        }

        try {
            //
            // We create a temp file to which the output will be written to
            // first, and then rename it to the target file name (because
            // compression can take a while)
            // If the temp file is already being written by another thread, this
            // method will block until it is complete and then return null
            //
            tmpOutputFile = getTmpOutputFile(file);
            if (tmpOutputFile == null) {
                return null;
            }

            // Create the directory if it doesn't already exist
            VirtualFile dir = VirtualFile.open(file.getRealFile().getParent());
            if (!dir.exists()) {
                if (!dir.getRealFile().mkdirs()) {
                    throw new PressException(
                            "Could not create directory for compressed file output "
                                    + file.getRealFile().getAbsolutePath());
                }
            }

            // Return a writer for the temporary file
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpOutputFile),
                    "UTF-8"));
            return writer;
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public void close() {
        if (writer == null) {
            throw new UnexpectedException(
                    "Writer has not yet been created. Call getWriter() and write to it.");
        }

        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        
        // Output was written to a temporary file, so rename it to overwrite the
        // true destination file.
        String msg = "Output written to temporary file\n%s\n"
                + "Moving from tmp path to final path:\n%s";
        String tmpPath = tmpOutputFile.getAbsolutePath();
        String finalPath = file.getRealFile().getAbsolutePath();
        PressLogger.trace(msg, tmpPath, finalPath);
        if (!tmpOutputFile.renameTo(file.getRealFile())) {
            String ex = "Successfully wrote compressed file to temporary path\n" + tmpPath;
            ex += "\nBut could not move it to final path\n" + finalPath;
            throw new PressException(ex);
        }
        tmpOutputFile = null;
    }

    private static File getTmpOutputFile(VirtualFile file) {
        String origPath = file.getRealFile().getAbsolutePath();
        File tmp = new File(origPath + ".tmp");

        // If the temp file already exists
        if (tmp.exists()) {
            long tmpLastModified = tmp.lastModified();
            long now = System.currentTimeMillis();

            // If the temp file is older than the destination file, or if it is
            // older than the allowed compression time, it must be a remnant of
            // a previous server crash so we can overwrite it
            if (tmpLastModified < file.lastModified()) {
                return tmp;
            }
            if (now - tmpLastModified > PluginConfig.maxCompressionTimeMillis) {
                return tmp;
            }

            // Otherwise it must be currently being written by another thread,
            // so wait for it to finish
            while (tmp.exists()) {
                if (System.currentTimeMillis() - now > PluginConfig.maxCompressionTimeMillis) {
                    throw new PressException("Timeout waiting for compressed file to be generated");
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }

            // Return null to indicate that the file was already generated by
            // another thread
            return null;
        }

        return tmp;
    }

    public static int clearFileCache(String compressedDir, String extension) {
        PressLogger.trace("Deleting cached files");

        // Get the cache directory
        VirtualFile dir = FileIO.getVirtualFile(compressedDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }

        int deletedFiles = deletePressFilesRecursively(dir.getRealFile(), extension);
        PressLogger.trace("Deleted %d cached files", deletedFiles);
        return deletedFiles;
    }
    
    
    private static int deletePressFilesRecursively(File directory, String extension) {
        int deletedFiles = 0;
        // First, delete compressed Press-files within this directory
        FileFilter compressedFileFilter = new PressFileFilter(extension);
        File[] files = directory.listFiles(compressedFileFilter);
        for (File file : files) {
            if (file.delete()) {
                deletedFiles++;
            }
        }

        // Second, recursively go through sub-directories of this directory
        FileFilter directoryFilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory();
            }
        };
        File[] subDirectories = directory.listFiles(directoryFilter);
        for (File subDir : subDirectories) {
            deletedFiles += deletePressFilesRecursively(subDir, extension);
        }
        return deletedFiles;
    }

    @Override
    public long length() {
        if (!exists()) {
            throw new PressException("Can't get length. File does not exist");
        }

        return file.length();
    }

}
