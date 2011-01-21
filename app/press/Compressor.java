package press;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.Play;
import play.PlayPlugin;
import play.cache.Cache;
import play.exceptions.UnexpectedException;
import play.libs.Crypto;
import play.mvc.Router;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.Router.ActionDefinition;
import play.templates.JavaExtensions;
import play.vfs.VirtualFile;

public abstract class Compressor extends PlayPlugin {
    static final String PRESS_SIGNATURE = "press-1.0";
    static final String PATTERN_TEXT = "^/\\*" + PRESS_SIGNATURE + "\\|(.*?)\\*/$";
    static final Pattern HEADER_PATTERN = Pattern.compile(PATTERN_TEXT);

    // File type, eg "JavaScript"
    String fileType;

    // File extension, eg ".js"
    String extension;

    // Play Action to get the compressed file, eg "Press.getCompressedJS"
    String getCompressedFileAction;

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

    // The list of files compressed as part of this request
    Map<String, List<FileInfo>> fileInfos;

    protected interface FileCompressor {
        public void compress(String fileName, Reader in, Writer out) throws Exception;
    }

    public Compressor(String fileType, String extension, String getCompressedFileAction,
            String tagName, String compressedTagName, String pressRequestStart,
            String pressRequestEnd, String srcDir, String compressedDir) {

        this.fileInfos = new HashMap<String, List<FileInfo>>();

        this.fileType = fileType;
        this.extension = extension;
        this.getCompressedFileAction = getCompressedFileAction;
        this.pressRequestStart = pressRequestStart;
        this.pressRequestEnd = pressRequestEnd;
        this.tagName = tagName;
        this.compressedTagName = compressedTagName;
        this.srcDir = PluginConfig.addTrailingSlash(srcDir);
        this.compressedDir = PluginConfig.addTrailingSlash(compressedDir);
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
        List<FileInfo> fileInfoList = fileInfos.get(fileName);
        if (fileInfoList == null) {
            fileInfoList = new ArrayList<FileInfo>();
            fileInfos.put(fileName, fileInfoList);
        }
        fileInfoList.add(new FileInfo(fileName, compress, null));

        return getFileRequestSignature(getFileNameWithIndex(fileName, (fileInfoList.size() - 1)));
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
        VirtualFile outputFile = getVirtualFile(outputFilePath);
        if (useCache(componentFiles, outputFile, extension) && outputFile.exists()) {
            PressLogger.trace("File has already been compressed");
        } else {
            // If so, generate it
            writeCompressedFile(compressor, componentFiles, outputFile, null);
        }

        return outputFilePath;
    }

    public String compressedUrl() {
        if (requestKey != null) {
            String msg = "There is more than one " + compressedTagName
                    + " tag in the template output. " + "There must be one only.";
            throw new PressException(msg);
        }

        requestKey = getRequestKey() + extension;
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("key", requestKey);
        ActionDefinition route = Router.reverse(getCompressedFileAction, params);

        int numFiles = getTotalFileCount();
        PressLogger.trace("Adding key %s for compression of %d files", requestKey, numFiles);

        return route.url;
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
                String msg = getTotalFileCount() + " files added to compression with ";
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
        if (namesInOrder.size() != getTotalFileCount()) {
            String msg = "Number of file compress requests found in response ";
            msg += "(" + namesInOrder.size() + ") ";
            msg += "not equal to number of files added to compression ";
            msg += "(" + fileInfos.size() + "). ";
            msg += "Please report a bug.";
            throw new PressException(msg);
        }

        // Copy the FileInfo from the map into an array, in order
        for (String fileNameWithIndex : namesInOrder) {
            String fileName = getFileName(fileNameWithIndex);
            int fileIndex = getFileIndex(fileNameWithIndex);
            if (!fileInfos.containsKey(fileName)) {
                String msg = "File compress request for '" + fileName + "' ";
                msg += "found in response but file was never added to file list. ";
                msg += "Please report a bug.";
                throw new PressException(msg);
            }

            filesInOrder.add(fileInfos.get(fileName).get(fileIndex));
        }

        return filesInOrder;
    }

    public void addFileListToCache(String cacheKey, List<FileInfo> originalList) {
        List<FileInfo> newList = new ArrayList<FileInfo>();
        for (FileInfo fileInfo : originalList) {
            VirtualFile file = getVirtualFile(srcDir + fileInfo.fileName);

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
        Cache.set(cacheKey, newList, PluginConfig.compressionKeyStorageTime);
    }

    /**
     * The request key is just the url - for the same url we should always
     * return the same compressed javascript or css.
     */
    private String getRequestKey() {
        String key = Request.current().path + Request.current().querystring + extension;

        // Get a hash of the url to keep it short
        String hashed = Crypto.passwordHash(key);

        return lettersOnly(hashed);
    }

    @SuppressWarnings("unchecked")
    protected static VirtualFile getCompressedFile(FileCompressor compressor, String key,
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

    protected static VirtualFile getCompressedFile(FileCompressor compressor,
            List<FileInfo> componentFiles, String compressedDir, String extension) {
        String joinedFileNames = JavaExtensions.join(FileInfo.getFileNames(componentFiles), "");
        String fileName = Crypto.passwordHash(joinedFileNames);
        fileName = lettersOnly(fileName);
        String filePath = compressedDir + fileName + extension;
        VirtualFile file = getVirtualFile(filePath);

        // If the file already exists in the cache, return it
        if (useCache(componentFiles, file, extension)) {
            String absolutePath = file.getRealFile().getAbsolutePath();
            if (file.exists()) {
                PressLogger.trace("Using existing compressed file %s", absolutePath);
                return file;
            } else {
                PressLogger.trace("Compressed file %s does not yet exist", absolutePath);
            }
        }

        PressLogger.trace("Generating compressed file %s from %d component files", file.getName(),
                componentFiles.size());

        //
        // We create a temp file to which the output will be written to first,
        // and then rename it to the target file name (because compression can
        // take a while)
        // If the temp file is already being written by another thread, this
        // method will block until it is complete and then return null
        //
        File tmp = getTmpOutputFile(file);
        if (tmp == null) {
            PressLogger.trace("Compressed file was generated by another thread");
        } else {
            writeCompressedFile(compressor, componentFiles, file, tmp);
        }

        return file;
    }

    private static void writeCompressedFile(FileCompressor compressor,
            List<FileInfo> componentFiles, VirtualFile file, File tmp) {

        // Create the directory if it doesn't already exist
        VirtualFile dir = VirtualFile.open(file.getRealFile().getParent());
        if (!dir.exists()) {
            if (!dir.getRealFile().mkdirs()) {
                throw new PressException("Could not create directory for compressed file output "
                        + file.getRealFile().getAbsolutePath());
            }
        }

        Writer out = null;
        try {
            // If there is a temp file specified, we write to that first
            // and then move it to the final destination file
            File destFile = tmp != null ? tmp : file.getRealFile();

            // Compress the component files and write the output to the
            // file
            out = new BufferedWriter(new FileWriter(destFile));

            // Add the last modified dates of each component file to the start
            // of the compressed file so that we can later check if any of them
            // have changed.
            String lastModifiedDates = createFileHeader(componentFiles);
            out.append(lastModifiedDates);

            long timeStart = System.currentTimeMillis();
            for (FileInfo componentFile : componentFiles) {
                compress(compressor, componentFile, out);
            }
            out.flush();
            out.close();
            long timeAfter = System.currentTimeMillis();
            PressLogger.trace("Time to compress files for '%s': %d milli-seconds", file
                    .getRealFile().getName(), (timeAfter - timeStart));

            // If the output was written to a temporary file, rename it to
            // overwrite the true destination file.
            if (tmp != null) {
                String msg = "Output written to temporary file\n%s\n";
                msg += "Moving from tmp path to final path:\n%s";
                String tmpPath = tmp.getAbsolutePath();
                String finalPath = file.getRealFile().getAbsolutePath();
                PressLogger.trace(msg, tmpPath, finalPath);
                if (!tmp.renameTo(file.getRealFile())) {
                    String ex = "Successfully wrote compressed file to temporary path\n" + tmpPath;
                    ex += "\nBut could not move it to final path\n" + finalPath;
                    throw new PressException(ex);
                }
            }

            PressLogger.trace("Compressed file generation complete:");
            PressLogger.trace(file.relativePath());

        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
            }
        }
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

    public static List<File> clearCache(String compressedDir, String extension) {
        PressLogger.trace("Deleting cached files");

        // Get the cache directory
        VirtualFile dir = getVirtualFile(compressedDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<File>();
        }

        // Get a list of all compressed files, and delete them
        FileFilter compressedFileFilter = new PressFileFilter(extension);
        File[] files = dir.getRealFile().listFiles(compressedFileFilter);
        List<File> deleted = new ArrayList<File>(files.length);
        for (File file : files) {
            if (file.delete()) {
                deleted.add(file);
            }
        }

        PressLogger.trace("Deleted %d cached files", deleted.size());
        return deleted;
    }

    private static boolean useCache(List<FileInfo> componentFiles, VirtualFile file,
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

    private static boolean haveComponentFilesChanged(List<FileInfo> componentFiles, VirtualFile file) {

        // Check if the file exists
        if (!file.exists()) {
            return true;
        }

        // Check if the file has a compression header
        String header = extractHeaderContent(file.getRealFile());
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
            String lastMod = fileInfo.file.lastModified().toString();
            char compress = fileInfo.compress ? 'c' : 'u';
            timestamps.add(compress + lastMod);
        }

        return "/*" + PRESS_SIGNATURE + "|" + JavaExtensions.join(timestamps, ":") + "*/\n";
    }

    public static String extractHeaderContent(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
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
        BufferedReader in = new BufferedReader(new FileReader(fileInfo.file.getRealFile()));

        // If the file should be compressed
        if (fileInfo.compress) {
            // Invoke the compressor
            PressLogger.trace("Compressing %s", fileName);
            compressor.compress(fileName, in, out);
        } else {
            // Otherwise just copy it
            PressLogger.trace("Adding already compressed file %s", fileName);
            write(in, out);
            compressor.compress(fileName, in, out);
        }
    }

    public static void write(Reader reader, Writer writer) throws IOException {
        int read = 0;
        char[] buffer = new char[8096];
        while ((read = reader.read(buffer)) > 0) {
            writer.write(buffer, 0, read);
        }
    }

    /**
     * Converts any non-letter characters in the given string to letters
     */
    private static String lettersOnly(String hashed) {
        char[] chars = hashed.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetter(chars[i])) {
                chars[i] = (char) ((int) 'A' + (int) chars[i] % 26);
            }
        }

        return new String(chars);
    }

    /**
     * Get the content of the response sent to the client as a String
     */
    protected static String getResponseContent() {
        try {
            String content = (String) Request.current().args.get("responseString");
            if (content == null) {
                content = Response.current.get().out.toString("utf-8");
                Request.current().args.put("responseString", content);
            }
            return content;
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

    private String getFileNameWithIndex(String fileName, int index) {
        return fileName + "[" + index + "]";
    }

    private String getFileName(String fileNameWithIndex) {
        int indexPart = fileNameWithIndex.lastIndexOf('[');
        return fileNameWithIndex.substring(0, indexPart);
    }

    private int getFileIndex(String fileNameWithIndex) {
        int indexPartBegin = fileNameWithIndex.lastIndexOf('[');
        String indexPart = fileNameWithIndex.substring(indexPartBegin + 1, fileNameWithIndex
                .length() - 1);
        return Integer.parseInt(indexPart);
    }

    private int getTotalFileCount() {
        int i = 0;
        for (String fileName : fileInfos.keySet()) {
            i += fileInfos.get(fileName).size();
        }

        return i;
    }

    /**
     * Gets the file with the given name. If the file does not exist in the
     * source directory, throws an exception.
     */
    public VirtualFile checkFileExists(String fileName) {
        return checkFileExists(fileName, srcDir);
    }

    /**
     * Gets the file with the given name. If the file does not exist in the
     * source directory, throws an exception.
     */
    public static VirtualFile checkFileExists(String fileName, String sourceDirectory) {
        VirtualFile srcFile = getVirtualFile(sourceDirectory + fileName);

        // Check the file exists
        if (!srcFile.exists()) {
            String msg = "Attempt to add file '" + srcFile.getRealFile().getAbsolutePath() + "' ";
            msg += "to compression but file does not exist.";
            throw new PressException(msg);
        }
        return srcFile;
    }

    /**
     * Gets the file at the given path, relative to the application root, even
     * if the file doesn't exist
     */
    private static VirtualFile getVirtualFile(String filePath) {
        VirtualFile vf = Play.getVirtualFile(filePath);
        if (vf == null) {
            return VirtualFile.open(Play.getFile(filePath));
        }
        return vf;
    }

    protected static class FileInfo {
        String fileName;
        boolean compress;
        private VirtualFile file;

        public FileInfo(String fileName, boolean compress, VirtualFile file) {
            this.fileName = fileName;
            this.compress = compress;
            this.file = file;
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
