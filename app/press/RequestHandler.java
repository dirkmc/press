package press;

import java.util.HashMap;
import java.util.Map;

import play.mvc.Router;
import play.vfs.VirtualFile;
import press.io.FileIO;

public abstract class RequestHandler {
    Map<String, Boolean> files = new HashMap<String, Boolean>();

    abstract String getTag(String src);

    abstract protected SourceFileManager getSourceManager();

    abstract protected CompressedFileManager getCompressedFileManager();

    abstract public String getCompressedUrl(String requestKey);

    abstract public String getSingleFileCompressionKey(String fileName);

    protected String getSingleFileCompressionKey(String fileName, SourceFileManager tmpManager) {
        PressLogger.trace("Request to compress single file %s", fileName);
        return tmpManager.addSingleFile(fileName, true);
    }

    public String getSrcDir() {
        return getSourceManager().srcDir;
    }

    public VirtualFile checkFileExists(String fileName) {
        return getSourceManager().checkFileExists(fileName);
    }

    public String add(String fileName, boolean packFile) {
        return getSourceManager().add(fileName, packFile);
    }

    public void saveFileList() {
        getSourceManager().saveFileList();
    }

    public String closeRequest() {
        return getSourceManager().closeRequest();
    }

    protected void checkForDuplicates(String fileName) {
        if (!files.containsKey(fileName)) {
            files.put(fileName, true);
            return;
        }

        SourceFileManager srcManager = getSourceManager();
        throw new DuplicateFileException(srcManager.getFileType(), fileName,
                srcManager.getTagName());
    }

    protected static String getCompressedUrl(String action, String requestKey) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("key", FileIO.escape(requestKey));
        return Router.reverse(action, params).url;
    }
}
