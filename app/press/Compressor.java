package press;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.PlayPlugin;
import play.cache.Cache;
import play.exceptions.UnexpectedException;
import play.libs.Crypto;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.templates.JavaExtensions;
import play.vfs.VirtualFile;
import press.io.CompressedFile;
import press.io.FileIO;

public abstract class Compressor extends PlayPlugin {
    static final String PRESS_SIGNATURE = "press-1.0";
    static final String PATTERN_TEXT = "^/\\*" + PRESS_SIGNATURE + "\\|(.*?)\\*/$";
    static final Pattern HEADER_PATTERN = Pattern.compile(PATTERN_TEXT);

    // File type, eg "JavaScript"
    String fileType;

    // File extension, eg ".js"
    String extension;

    // Tag name, eg "#{press.script}"
    String tagName;

    // Compressed file tag name, eg "#{press.compressed-script}"
    String compressedTagName;

    // Signatures for the start and end of a request to compress a file,
    // eg "<!-- press js: " and " -->" would result in a compress request like:
    // <!-- press js: /public/javascript/myfile.js -->
    String pressRequestStart;
    String pressRequestEnd;

    // Directory where the source files are read from, eg "/public/javascripts"
    String srcDir;

    // Directory for the compressed output, eg "/public/javascripts/js"
    String compressedDir;

    // The key used to identify this request
    String requestKey = null;

    // Keep track of the response object created when compression started. It
    // can change if there's a 404 or 500 error.
    Response currentResponse;

    // The list of files compressed as part of this request
    Map<String, FileInfo> fileInfos;

    protected interface FileCompressor {
        public void compress(String fileName, Reader in, Writer out) throws Exception;
    }

    public Compressor(String fileType, String extension, String tagName, String compressedTagName,
            String pressRequestStart, String pressRequestEnd, String srcDir, String compressedDir) {

        this.fileInfos = new HashMap<String, FileInfo>();
        this.currentResponse = Response.current();

        this.fileType = fileType;
        this.extension = extension;
        this.pressRequestStart = pressRequestStart;
        this.pressRequestEnd = pressRequestEnd;
        this.tagName = tagName;
        this.compressedTagName = compressedTagName;
        this.srcDir = PluginConfig.addTrailingSlash(srcDir);
        this.compressedDir = PluginConfig.addTrailingSlash(compressedDir);
    }

    /**
     * Called when compressing a single file (as opposed to a group of files).
     * Compresses the file on the fly, and returns the url to it. The file will
     * have the same name as the original file with .min appended before the
     * extension. eg the compressed output of widget.js will be in widget.min.js
     */
    public String compressedSingleFileUrl(FileCompressor compressor, String fileName) {
        PressLogger.trace("Request to compress single file %s", fileName);

        int lastDot = fileName.lastIndexOf('.');
        String compressedFileName = fileName.substring(0, lastDot) + ".min";
        compressedFileName += fileName.substring(lastDot);

        // The process for compressing a single file is the same as for a group
        // of files, the list just has a single entry
        VirtualFile srcFile = checkFileExists(fileName);
        List<FileInfo> componentFiles = new ArrayList<FileInfo>(1);
        componentFiles.add(new FileInfo(compressedFileName, true, srcFile));

        // Check whether the compressed file needs to be generated
        String outputFilePath = compressedDir + compressedFileName;
        CompressedFile outputFile = CompressedFile.create(outputFilePath);
        if (outputFile.exists() && useCache(componentFiles, outputFile, extension)) {
            // If not, return the existing file
            PressLogger.trace("File has already been compressed");
        } else {
            // If so, generate it
            writeCompressedFile(compressor, componentFiles, outputFile);
        }

        return outputFilePath;
    }

    public static CompressedFile getSingleCompressedFile(String requestKey) {
        return CompressedFile.create(requestKey);
    }

    /**
     * Adds a file to the list of files to be compressed
     * 
     * @return the file request signature to be output in the HTML
     */
    public String add(String fileName, boolean compress) {
        if (compress) {
            PressLogger.trace("Adding %s to output", fileName);
        } else {
            PressLogger.trace("Adding uncompressed file %s to output", fileName);
        }

        if (fileInfos.containsKey(fileName)) {
            throw new DuplicateFileException(fileType, fileName, tagName);
        }

        // Check that the file exists
        checkFileExists(fileName);

        // Add the file to the list of files to be compressed
        fileInfos.put(fileName, new FileInfo(fileName, compress, null));

        return getFileRequestSignature(fileName);
    }

    /**
     * This must only be called once, as it indicates that the file is ready to
     * be output
     * 
     * @return the request key used to retrieve the compressed file
     */
    public String closeRequest() {
        if (requestKey != null) {
            String msg = "There is more than one " + compressedTagName
                    + " tag in the template output. " + "There must be one only.";
            throw new PressException(msg);
        }
        requestKey = getRequestKey() + extension;

        PressLogger.trace("Adding key %s for compression of %d files", requestKey, fileInfos.size());

        return requestKey;
    }

    /**
     * The request key is is derived from the list of files - for the same list
     * of files we should always return the same compressed javascript or css.
     */
    private String getRequestKey() {
        String key = "";
        for (String fileName : fileInfos.keySet()) {
            key += fileName;
        }

        // Get a hash of the url to keep it short
        String hashed = Crypto.passwordHash(key);
        return FileIO.lettersOnly(hashed);
    }

    public void saveFileList() {
        // If the request key has not been set, that means there was no request
        // for compressed source anywhere in the template file, so we don't
        // need to generate anything
        if (requestKey == null) {
            // If the file list is not empty, then there have been files added
            // to compression but they will not be output. So throw an
            // exception telling the user he needs to add some files.
            if (fileInfos.size() > 0) {
                String msg = fileInfos.size() + " files added to compression with ";
                msg += tagName + " tag but no " + compressedTagName + " tag was found. ";
                msg += "You must include a " + compressedTagName + " tag in the template ";
                msg += "to output the compressed content of these files: ";
                msg += JavaExtensions.join(fileInfos.keySet(), ", ");

                throw new PressException(msg);
            }

            return;
        }

        // The press tag may not always have been executed by the template
        // engine in the same order that the resulting <script> tags would
        // appear in the HMTL output. So here we scan the output to figure out
        // in what order the <script> tags should actually be output.
        long timeStart = System.currentTimeMillis();
        List<FileInfo> orderedFileNames = getFileListOrder();
        long timeAfter = System.currentTimeMillis();
        PressLogger.trace("Time to scan response for %s files for '%s': %d milli-seconds",
                fileType, Request.current().url, (timeAfter - timeStart));

        // Add the list of files to the cache.
        // When the server receives a request for the compressed file, it will
        // retrieve the list of files and compress them.
        addFileListToCache(requestKey, orderedFileNames);
    }

    public List<FileInfo> getFileListOrder() {
        String content = getResponseContent();
        List<String> namesInOrder = getFilesInResponse(content);
        List<FileInfo> filesInOrder = new ArrayList<FileInfo>(namesInOrder.size());

        // Do some sanity checking
        if (namesInOrder.size() != fileInfos.size()) {
            String msg = "Number of file compress requests found in response ";
            msg += "(" + namesInOrder.size() + ") ";
            msg += "not equal to number of files added to compression ";
            msg += "(" + fileInfos.size() + "). ";
            msg += "Please report a bug.\n";
            msg += "Note: Do not use press tags within a 404.html or 500.html ";
            msg += "template, it will not work.";
            throw new PressException(msg);
        }

        // Copy the FileInfo from the map into an array, in order
        for (String fileName : namesInOrder) {
            if (!fileInfos.containsKey(fileName)) {
                String msg = "File compress request for '" + fileName + "' ";
                msg += "found in response but file was never added to file list. ";
                msg += "Please report a bug.";
                throw new PressException(msg);
            }

            filesInOrder.add(fileInfos.get(fileName));
        }

        return filesInOrder;
    }

    public void addFileListToCache(String cacheKey, List<FileInfo> originalList) {
        List<FileInfo> newList = new ArrayList<FileInfo>();
        for (FileInfo fileInfo : originalList) {
            VirtualFile file = FileIO.getVirtualFile(srcDir + fileInfo.fileName);

            // Check the file exists
            if (!file.exists()) {
                String msg = "Attempt to add file '" + file.getRealFile().getAbsolutePath() + "' ";
                msg += "to compression with " + tagName + " tag but file does not exist.";
                throw new PressException(msg);
            }

            newList.add(new FileInfo(fileInfo.fileName, fileInfo.compress, file));
        }

        // Add a mapping between the request key and the list of files that
        // are compressed for the request
        Cache.safeSet(cacheKey, newList, PluginConfig.compressionKeyStorageTime);
    }

    @SuppressWarnings("unchecked")
    protected static CompressedFile getCompressedFile(FileCompressor compressor, String key,
            String compressedDir, String extension) {

        List<FileInfo> componentFiles = (List<FileInfo>) Cache.get(key);

        // If there was nothing found for the given request key, return null.
        // This shouldn't happen unless there was a very long delay between the
        // template being rendered and the compressed file being requested
        if (componentFiles == null) {
            return null;
        }

        return getCompressedFile(compressor, componentFiles, compressedDir, extension);
    }

    protected static CompressedFile getCompressedFile(FileCompressor compressor,
            List<FileInfo> componentFiles, String compressedDir, String extension) {
        String joinedFileNames = JavaExtensions.join(FileInfo.getFileNames(componentFiles), "");
        String fileName = Crypto.passwordHash(joinedFileNames);
        fileName = FileIO.lettersOnly(fileName);
        String filePath = compressedDir + fileName + extension;
        CompressedFile file = null;
        file = CompressedFile.create(filePath);

        // If the file already exists in the cache, return it
        boolean exists = file.exists();
        if (exists && useCache(componentFiles, file, extension)) {
            PressLogger.trace("Using existing compressed file %s", filePath);
            return file;
        }

        if (!exists) {
            PressLogger.trace("Compressed file %s does not yet exist", filePath);
        }
        PressLogger.trace("Generating compressed file %s from %d component files", filePath,
                componentFiles.size());

        writeCompressedFile(compressor, componentFiles, file);
        return file;
    }

    private static void writeCompressedFile(FileCompressor compressor,
            List<FileInfo> componentFiles, CompressedFile file) {

        long timeStart = System.currentTimeMillis();

        // If the file is being written by another thread, startWrite() will
        // block until it is complete and then return null
        Writer writer = file.startWrite();
        if (writer == null) {
            PressLogger.trace("Compressed file was generated by another thread");
            return;
        }

        try {
            // Add the last modified dates of each component file to the
            // start of the compressed file so that we can later check if
            // any of them have changed.
            String lastModifiedDates = createFileHeader(componentFiles);
            writer.append(lastModifiedDates);

            for (FileInfo componentFile : componentFiles) {
                compress(compressor, componentFile, writer);
            }

            long timeAfter = System.currentTimeMillis();
            PressLogger.trace("Time to compress files for '%s': %d milli-seconds", FileIO
                    .getFileNameFromPath(file.name()), (timeAfter - timeStart));
        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            file.close();
        }
    }

    public static int clearCache(String compressedDir, String extension) {
        return CompressedFile.clearCache(compressedDir, extension);
    }

    private static boolean useCache(List<FileInfo> componentFiles, CompressedFile file,
            String extension) {
        PressLogger.trace("Caching strategy is %s", PluginConfig.cache);
        if (PluginConfig.cache.equals(CachingStrategy.Never)) {
            return false;
        }

        if (PluginConfig.cache.equals(CachingStrategy.Always)) {
            return true;
        }

        boolean changed = haveComponentFilesChanged(componentFiles, file);
        if (changed) {
            PressLogger.trace("Component %s files have changed", extension);
        } else {
            PressLogger.trace("Component %s files have not changed", extension);
        }

        return !changed;
    }

    private static boolean haveComponentFilesChanged(List<FileInfo> componentFiles,
            CompressedFile file) {

        // Check if the file exists
        if (!file.exists()) {
            return true;
        }

        // Check if the file has a compression header
        String header = extractHeaderContent(file);
        if (header == null) {
            return true;
        }

        // Check if the number of files has changed
        String[] lastModifieds = header.split(":");
        if (lastModifieds.length != componentFiles.size()) {
            return true;
        }

        try {
            // Check each of the stored last modified dates against the file's
            // current last modified date
            for (int i = 0; i < componentFiles.size(); i++) {
                FileInfo fileInfo = componentFiles.get(i);

                // Check if the file was compressed and is now uncompressed,
                // or vice versa
                char compress = fileInfo.compress ? 'c' : 'u';
                if (lastModifieds[i].charAt(0) != compress) {
                    return true;
                }

                // Check the timestamp
                String lastMod = lastModifieds[i].substring(1);
                long lastModified = Long.parseLong(lastMod);
                if (fileInfo.file.lastModified() != lastModified) {
                    return true;
                }
            }
        } catch (Exception e) {
            // If there's any sort of problem reading the header, we can just
            // overwrite the file
            return true;
        }

        return false;
    }

    /**
     * <pre>
     * The file header consists of
     * - An opening comment
     * - A signature
     * - A '|' character used as a separator
     * - A ':' separated list of
     *   o the character 'c' or 'u', indicating whether the file is compressed
     *   o a unix timestamp
     * - A closing comment
     * 
     * eg
     * press-1.0|c12323123:u1231212:c1312312:c1312423
     * </pre>
     * 
     */
    public static String createFileHeader(List<FileInfo> componentFiles) {
        List<String> timestamps = new ArrayList<String>(componentFiles.size());

        for (int i = 0; i < componentFiles.size(); i++) {
            FileInfo fileInfo = componentFiles.get(i);
            String lastMod = Long.toString(fileInfo.file.lastModified());
            char compress = fileInfo.compress ? 'c' : 'u';
            timestamps.add(compress + lastMod);
        }

        return "/*" + PRESS_SIGNATURE + "|" + JavaExtensions.join(timestamps, ":") + "*/\n";
    }

    public static String extractHeaderContent(CompressedFile file) {
        try {
            if (!file.exists()) {
                return null;

            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.inputStream()));
            String firstLine = reader.readLine();
            Matcher matcher = HEADER_PATTERN.matcher(firstLine);
            if (matcher.matches()) {
                return matcher.group(1);
            }
            return null;

        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private static void compress(FileCompressor compressor, FileInfo fileInfo, Writer out)
            throws Exception {

        String fileName = fileInfo.file.getName();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(
                fileInfo.file), "UTF-8"));

        // If the file should be compressed
        if (fileInfo.compress) {
            // Invoke the compressor
            PressLogger.trace("Compressing %s", fileName);
            compressor.compress(fileName, in, out);
        } else {
            // Otherwise just copy it
            PressLogger.trace("Adding already compressed file %s", fileName);
            FileIO.write(in, out);
            compressor.compress(fileName, in, out);
        }
    }

    /**
     * Get the content of the response sent to the client as a String
     */
    protected String getResponseContent() {
        try {
            return currentResponse.out.toString("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnexpectedException(e);
        }
    }

    protected String getFileRequestSignature(String fileName) {
        return pressRequestStart + fileName + pressRequestEnd;
    }

    protected List<String> getFilesInResponse(String content) {
        List<String> filesInOrder = new ArrayList<String>();

        int startIndex = content.indexOf(pressRequestStart);
        while (startIndex != -1) {
            int endIndex = content.indexOf(pressRequestEnd, startIndex);
            if (endIndex == -1) {
                return filesInOrder;
            }

            int fileNameStartIndex = startIndex + pressRequestStart.length();
            String foundFileName = content.substring(fileNameStartIndex, endIndex);
            filesInOrder.add(foundFileName);

            startIndex = content.indexOf(pressRequestStart, endIndex);
        }

        return filesInOrder;
    }

    /**
     * Gets the file with the given name. If the file does not exist in the
     * source directory, throws an exception.
     */
    public VirtualFile checkFileExists(String fileName) {
        return FileIO.checkFileExists(fileName, srcDir);
    }

    protected static class FileInfo implements Serializable {
        String fileName;
        boolean compress;
        private File file;

        public FileInfo(String fileName, boolean compress, VirtualFile file) {
            this.fileName = fileName;
            this.compress = compress;
            this.file = file == null ? null : file.getRealFile();
        }

        public static Collection<String> getFileNames(List<FileInfo> list) {
            Collection<String> fileNames = new ArrayList<String>(list.size());
            for (FileInfo fileInfo : list) {
                fileNames.add(fileInfo.fileName);
            }

            return fileNames;
        }
    }
}
