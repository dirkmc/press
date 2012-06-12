package press;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import play.PlayPlugin;
import play.cache.Cache;
import play.exceptions.UnexpectedException;
import play.libs.Crypto;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.templates.JavaExtensions;
import play.vfs.VirtualFile;
import press.io.FileIO;

public abstract class SourceFileManager extends PlayPlugin {

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

    // The key used to identify this request
    String requestKey = null;

    // Keep track of the response object created when rendering started. It
    // can change if there's a 404 or 500 error.
    Response currentResponse;

    // The list of files compressed as part of this request
    Map<String, FileInfo> fileInfos;

    public SourceFileManager(String fileType, String extension, String tagName,
            String compressedTagName, String pressRequestStart, String pressRequestEnd,
            String srcDir) {

        this.fileInfos = new HashMap<String, FileInfo>();
        this.currentResponse = Response.current();

        this.fileType = fileType;
        this.extension = extension;
        this.pressRequestStart = pressRequestStart;
        this.pressRequestEnd = pressRequestEnd;
        this.tagName = tagName;
        this.compressedTagName = compressedTagName;
        this.srcDir = PluginConfig.addTrailingSlash(srcDir);
    }

    public String getTagName() {
        return tagName;
    }

    public String getFileType() {
        return fileType;
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

        // Add the file to the list of files to be compressed
        fileInfos.put(fileName, new FileInfo(fileName, compress, checkFileExists(fileName)));

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

        PressLogger
                .trace("Adding key %s for compression of %d files", requestKey, fileInfos.size());

        return requestKey;
    }

    /**
     * The request key is is derived from the list of files - for the same list
     * of files we should always return the same compressed javascript or css.
     */
    private String getRequestKey() {
        String key = "";
        for (Entry<String, FileInfo> entry : fileInfos.entrySet()) {
            key += entry.getKey();
            // If we use the 'Change' caching strategy, make the modified
            // timestamp
            // of each file part of the key.
            if (PluginConfig.cache.equals(CachingStrategy.Change)) {
                key += entry.getValue().getLastModified();
            }
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

    /**
     * Gets the the list of source files for the given request key
     */
    public static List<FileInfo> getSourceFiles(String key) {
        return (List<FileInfo>) Cache.get(key);
    }
}
