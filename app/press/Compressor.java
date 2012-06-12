package press;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.PlayPlugin;
import play.exceptions.UnexpectedException;
import play.libs.Crypto;
import play.templates.JavaExtensions;
import play.vfs.VirtualFile;
import press.io.CompressedFile;
import press.io.FileIO;

public abstract class Compressor extends PlayPlugin {
    static final String PRESS_SIGNATURE = "press-1.0";
    static final String PATTERN_TEXT = "^/\\*" + PRESS_SIGNATURE + "\\*/$";
    static final Pattern HEADER_PATTERN = Pattern.compile(PATTERN_TEXT);

    // Directory where the source files are read from, eg "/public/javascripts"
    String srcDir;

    // Directory for the compressed output, eg "/public/javascripts/press/js"
    String compressedDir;

    // The extension of the output file, eg ".js"
    private String extension;

    public Compressor(String srcDir, String compressedDir, String extension) {
        this.srcDir = PluginConfig.addTrailingSlash(srcDir);
        this.compressedDir = PluginConfig.addTrailingSlash(compressedDir);
        this.extension = extension;
    }

    /**
     * Called when compressing a single file (as opposed to a group of files).
     * Compresses the file on the fly, and returns the url to it. The file will
     * have the same name as the original file with .min appended before the
     * extension. eg the compressed output of widget.js will be in widget.min.js
     */
    public String compressedSingleFileUrl(String fileName) {
        PressLogger.trace("Request to compress single file %s", fileName);
        VirtualFile srcFile = FileIO.checkFileExists(fileName, srcDir);

        int lastDot = fileName.lastIndexOf('.');
        String compressedFileName = fileName.substring(0, lastDot) + "." + srcFile.lastModified()
                + ".min";
        compressedFileName += fileName.substring(lastDot);

        // The process for compressing a single file is the same as for a group
        // of files, the list just has a single entry
        List<FileInfo> componentFiles = new ArrayList<FileInfo>(1);
        componentFiles.add(new FileInfo(compressedFileName, true, srcFile));

        // Check whether the compressed file needs to be generated
        String outputFilePath = compressedDir + compressedFileName;
        CompressedFile outputFile = CompressedFile.create(outputFilePath);
        if (outputFile.exists() && useCache()) {
            // If not, return the existing file
            PressLogger.trace("File has already been compressed");
        } else {
            // If so, generate it
            writeCompressedFile(componentFiles, outputFile);
        }

        return outputFilePath;
    }

    public CompressedFile getSingleCompressedFile(String requestKey) {
        // The compressed file has already been created, just retrieve its
        // contents
        return CompressedFile.create(requestKey);
    }

    public CompressedFile getCompressedFile(String key) {
        List<FileInfo> componentFiles = SourceFileManager.getSourceFiles(key);

        // If there was nothing found for the given request key, return null.
        // This shouldn't happen unless there was a very long delay between the
        // template being rendered and the compressed file being requested
        if (componentFiles == null) {
            return null;
        }

        return getCompressedFile(componentFiles, compressedDir);
    }

    protected CompressedFile getCompressedFile(List<FileInfo> componentFiles, String compressedDir) {

        String joinedFileNames = null;
        if (PluginConfig.cache.equals(CachingStrategy.Change)) {
            joinedFileNames = JavaExtensions.join(
                    FileInfo.getFileNamesAndModifiedTimestamps(componentFiles), "");
        } else {
            joinedFileNames = JavaExtensions.join(FileInfo.getFileNames(componentFiles), "");
        }

        String fileName = Crypto.passwordHash(joinedFileNames);
        fileName = FileIO.lettersOnly(fileName);
        String filePath = compressedDir + fileName + extension;
        CompressedFile file = null;
        file = CompressedFile.create(filePath);

        // If the file already exists in the cache, return it
        boolean exists = file.exists();
        if (exists && useCache()) {
            PressLogger.trace("Using existing compressed file %s", filePath);
            return file;
        }

        if (!exists) {
            PressLogger.trace("Compressed file %s does not yet exist", filePath);
        }
        PressLogger.trace("Generating compressed file %s from %d component files", filePath,
                componentFiles.size());

        writeCompressedFile(componentFiles, file);
        return file;
    }

    private void writeCompressedFile(List<FileInfo> componentFiles, CompressedFile file) {

        long timeStart = System.currentTimeMillis();

        // If the file is being written by another thread, startWrite() will
        // block until it is complete and then return null
        Writer writer = file.startWrite();
        if (writer == null) {
            PressLogger.trace("Compressed file was generated by another thread");
            return;
        }

        try {
            writer.append(createFileHeader());

            for (FileInfo componentFile : componentFiles) {
                compress(componentFile, writer);
            }

            long timeAfter = System.currentTimeMillis();
            PressLogger.trace("Time to compress files for '%s': %d milli-seconds",
                    FileIO.getFileNameFromPath(file.name()), (timeAfter - timeStart));
        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            // Note that this flushes and closes the writer as well
            file.close();
        }
    }

    private void compress(FileInfo fileInfo, Writer out) throws Exception {
        String fileName = fileInfo.file.getName();

        // If the file should be compressed
        if (fileInfo.compress) {
            // Invoke the compressor
            PressLogger.trace("Compressing %s", fileName);
            compress(fileInfo.file, out);
        } else {
            // Otherwise just copy it
            PressLogger.trace("Adding already compressed file %s", fileName);
            FileIO.write(FileIO.getReader(fileInfo.file), out);
        }
    }

    public static int clearCache(String compressedDir, String extension) {
        return CompressedFile.clearCache(compressedDir, extension);
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

    public static String createFileHeader() {
        return "/*" + PRESS_SIGNATURE + "*/\n";
    }

    public static boolean hasPressHeader(CompressedFile file) {
        try {
            if (!file.exists()) {
                return false;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.inputStream()));
            String firstLine = reader.readLine();
            Matcher matcher = HEADER_PATTERN.matcher(firstLine);
            if (matcher.matches()) {
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    abstract protected void compress(File file, Writer out) throws IOException;
}
