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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    List<String> fileNames;
    Set<String> fileNamesSet;

    protected interface FileCompressor {
        public void compress(String fileName, Reader in, Writer out) throws Exception;
    }

    public Compressor(String fileType, String extension, String getCompressedFileAction,
            String tagName, String compressedTagName, String pressRequestStart,
            String pressRequestEnd, String srcDir, String compressedDir) {

        this.fileNames = new ArrayList<String>();
        this.fileNamesSet = new HashSet<String>();

        this.fileType = fileType;
        this.extension = extension;
        this.getCompressedFileAction = getCompressedFileAction;
        this.pressRequestStart = pressRequestStart;
        this.pressRequestEnd = pressRequestEnd;
        this.tagName = tagName;
        this.compressedTagName = compressedTagName;
        this.srcDir = addTrailingSlash(srcDir);
        this.compressedDir = addTrailingSlash(compressedDir);
    }

    private String addTrailingSlash(String dir) {
        if (dir.charAt(dir.length() - 1) != '/') {
            return dir + '/';
        }

        return dir;
    }

    /**
     * Adds a file to the list of files to be compressed
     * 
     * @return the file request signature to be output in the HTML
     */
    public String add(String fileName) {
        PressLogger.trace("Adding %s to compression", fileName);

        fileNames.add(fileName);
        fileNamesSet.add(fileName);
        if (fileNames.size() != fileNamesSet.size()) {
            String msg = "Attempt to add the same " + fileType + " file ";
            msg += "to compression twice: '" + fileName + "'\n";
            msg += "Check that you're not including the same file in two different ";
            msg += tagName + " tags";
            throw new PressException(msg);
        }

        return getFileRequestSignature(fileName);
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

        int numFiles = fileNames.size();
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
            if (fileNames.size() > 0) {
                String msg = fileNames.size() + " files added to compression with ";
                msg += tagName + " tag but no " + compressedTagName + " tag was found. ";
                msg += "You must include a " + compressedTagName + " tag in the template ";
                msg += "to output the compressed content of these files: ";
                msg += JavaExtensions.join(fileNames, ", ");

                throw new PressException(msg);
            }

            return;
        }

        // The press tag may not always been executed by the template engine
        // in the same order that the resulting <script> tags would appear in
        // the HMTL output. So here we scan the output to figure out in what
        // order the <script> tags should actually be output.
        long timeStart = System.currentTimeMillis();
        List<String> orderedFileNames = getFileListOrder();
        long timeAfter = System.currentTimeMillis();
        PressLogger.trace("Time to scan response for %s files for '%s': %d milli-seconds",
                fileType, Request.current().url, (timeAfter - timeStart));

        // Add the list of files to the cache.
        // When the server receives a request for the compressed file, it will
        // retrieve the list of files and compress them.
        addFileListToCache(requestKey, orderedFileNames);
    }

    public List<String> getFileListOrder() {
        String content = getResponseContent();
        List<String> filesInOrder = getFilesInResponse(content);

        // Do some sanity checking
        for (String fileInOrder : filesInOrder) {
            if (!fileNamesSet.contains(fileInOrder)) {
                String msg = "File compress request for '" + fileInOrder + "' ";
                msg += "found in response but file was never added to file list. ";
                msg += "Please disable the press plugin and report a bug.";
                throw new PressException(msg);
            }
        }

        if (filesInOrder.size() != fileNamesSet.size()) {
            String msg = "Number of file compress requests found in response ";
            msg += "(" + filesInOrder.size() + ")";
            msg += "not equal to number of files added to compression ";
            msg += "(" + fileNamesSet.size() + "). ";
            msg += "Please disable the press plugin and report a bug.";
            throw new PressException(msg);
        }

        return filesInOrder;
    }

    public void addFileListToCache(String cacheKey, List<String> fileNamesList) {
        List<VirtualFile> fileList = new ArrayList<VirtualFile>();
        for (String fileName : fileNamesList) {
            VirtualFile file = VirtualFile.fromRelativePath(srcDir + fileName);

            // Check the file exists
            if (!file.exists()) {
                String msg = "Attempt to add file '" + file.getRealFile().getAbsolutePath() + "' ";
                msg += "to compression with " + tagName + " tag but file does not exist.";
                throw new PressException(msg);
            }

            fileList.add(file);
        }

        // Add a mapping between the request key and the list of files that
        // are compressed for the request
        Cache.set(cacheKey, fileList, PluginConfig.compressionKeyStorageTime);
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
        List<VirtualFile> componentFiles = (List<VirtualFile>) Cache.get(key);

        // If there was nothing found for the given request key, return null.
        // This shouldn't happen unless there was a very long delay between the
        // template being rendered and the compressed file being requested
        if (componentFiles == null) {
            return null;
        }

        return getCompressedFile(compressor, componentFiles, compressedDir, extension);
    }

    protected static VirtualFile getCompressedFile(FileCompressor compressor,
            List<VirtualFile> componentFiles, String compressedDir, String extension) {
        String fileName = Crypto.passwordHash(JavaExtensions.join(componentFiles, ""));
        fileName = lettersOnly(fileName);
        String filePath = compressedDir + fileName + extension;
        VirtualFile file = VirtualFile.fromRelativePath(filePath);

        // If the file already exists in the cache, return it
        if (useCache(componentFiles, file, extension)) {
            if (file.exists()) {
                return file;
            } else {
                PressLogger.trace("Compressed file %s does not yet exist", file.getName());
            }
        }

        PressLogger.trace("Generating compressed file %s from %d component files", file.getName(),
                componentFiles.size());

        // Create the directory if it doesn't already exist
        VirtualFile dir = VirtualFile.fromRelativePath(compressedDir);
        if (!dir.exists()) {
            if (!dir.getRealFile().mkdirs()) {
                throw new PressException("Could not create directory for compressed file output "
                        + compressedDir);
            }
        }

        Writer out = null;
        try {
            // Compress the component files and write the output to a temporary
            // file
            File tmp = File.createTempFile("prs", extension);
            out = new BufferedWriter(new FileWriter(tmp));

            // Add the last modified dates of each component file to the start
            // of the compressed file so that we can later check if any of them
            // have changed.
            String lastModifiedDates = createFileHeader(componentFiles);
            out.append(lastModifiedDates);

            for (VirtualFile componentFile : componentFiles) {
                compress(compressor, componentFile, out);
            }

            // Once the compressed output has been written to the temporary
            // file, move it to the cache directory.
            tmp.renameTo(file.getRealFile());
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

        return file;
    }

    public static List<File> clearCache(String compressedDir, String extension) {
        PressLogger.trace("Deleting cached files");

        // Get the cache directory
        VirtualFile dir = VirtualFile.fromRelativePath(compressedDir);
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

    private static boolean useCache(List<VirtualFile> componentFiles, VirtualFile file,
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

    private static boolean haveComponentFilesChanged(List<VirtualFile> componentFiles,
            VirtualFile file) {

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
                long lastModified = Long.parseLong(lastModifieds[i]);
                if (componentFiles.get(i).lastModified() != lastModified) {
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
     * - A list of unix timestamps separated by the ':' character
     * - A closing comment
     * 
     * eg
     * press-1.0|12323123:1231212:1312312
     * </pre>
     * 
     */
    public static String createFileHeader(List<VirtualFile> componentFiles) {
        List<String> timestamps = new ArrayList<String>(componentFiles.size());

        for (int i = 0; i < componentFiles.size(); i++) {
            VirtualFile file = componentFiles.get(i);
            String lastMod = file.lastModified().toString();
            timestamps.add(lastMod);
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

    private static void compress(FileCompressor compressor, VirtualFile file, Writer out)
            throws Exception {
        String fileName = file.getName();
        PressLogger.trace("Compressing %s", fileName);
        BufferedReader in = new BufferedReader(new FileReader(file.getRealFile()));
        compressor.compress(fileName, in, out);
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
}
