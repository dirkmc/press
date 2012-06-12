package press;

import java.util.HashMap;
import java.util.Map;

import play.mvc.Router;
import press.io.FileIO;

public abstract class RequestHandler {
    Map<String, Boolean> files = new HashMap<String, Boolean>();

    abstract protected SourceFileManager getSourceManager();

    abstract protected Compressor getCompressor();

    abstract String getSingleCompressedUrl(String requestKey);

    abstract public String getMultiCompressedUrl(String requestKey);

    abstract String getTag(String src);

    public String getSrcDir() {
        return getSourceManager().srcDir;
    }

    public void checkFileExists(String fileName) {
        FileIO.checkFileExists(fileName, getSrcDir());
    }

    public String compressedSingleFileUrl(String fileName) {
        return getCompressor().compressedSingleFileUrl(fileName);
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
