package press.io;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import play.cache.Cache;
import play.exceptions.UnexpectedException;
import press.PluginConfig;
import press.PressException;
import press.PressLogger;

public class InMemoryCompressedFile extends CompressedFile {
    private static final String FILE_LIST_KEY = "InMemoryFileList";
    private InputStream inputStream;
    private Writer writer;
    private ByteArrayOutputStream outputStream;
    private byte[] bytes;
    private static final String A_VERY_LONG_TIME = "3650d";

    public InMemoryCompressedFile(String filePath) {
        super(filePath);
    }

    @Override
    public boolean exists() {
        if (inputStream != null) {
            return true;
        }

        long startTime = System.currentTimeMillis();
        bytes = (byte[]) Cache.get(getCacheKey());
        if (bytes != null) {
            long totalTime = System.currentTimeMillis() - startTime;
            PressLogger.trace("Got file of size %d bytes from cache in %d milli-seconds.",
                    bytes.length, totalTime);
            inputStream = new ByteArrayInputStream(bytes);
            return true;
        }

        return false;
    }

    @Override
    public InputStream inputStream() {
        if (!exists()) {
            throw new PressException("Can't create InputStream. File with key " + getCacheKey()
                    + " does not exist in cache");
        }

        return inputStream;
    }

    @Override
    public String name() {
        return FileIO.getFileNameFromPath(getFilePath());
    }

    @Override
    public Writer startWrite() {
        // Compression might take a while, so if we're already writing out the
        // compressed file form a different thread, return null
        String inProgressKey = getInProgressKey(getFilePath());
        if (Cache.get(inProgressKey) != null) {
            return null;
        }
        String expiration = (PluginConfig.maxCompressionTimeMillis / 1000) + "s";
        Cache.safeSet(inProgressKey, true, expiration);

        if (writer == null) {
            outputStream = new ByteArrayOutputStream();
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        }

        return writer;
    }

    @Override
    public void close() {
        if (writer == null) {
            throw new PressException(
                    "Output stream has not yet been created. Call getWriter() and write to it.");
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        byte[] outBytes = outputStream.toByteArray();
        PressLogger.trace("Saving file of size %d bytes to cache.", outBytes.length);
        addFileToCache(getFilePath(), outBytes);
    }

    private static String getInProgressKey(String filePath) {
        return "in-progress-" + filePath;
    }

    private String getCacheKey() {
        return getCacheKey(getFilePath());
    }

    private static String getCacheKey(String filePath) {
        return "file-" + filePath;
    }

    private void addFileToCache(String filePath, byte[] outBytes) {
        long startTime = System.currentTimeMillis();

        List<String> fileList = getFileList();
        fileList.add(filePath);
        Cache.set(FILE_LIST_KEY, fileList, A_VERY_LONG_TIME);

        String cacheKey = getCacheKey(filePath);
        if (!Cache.safeSet(cacheKey, outBytes, A_VERY_LONG_TIME)) {
            throw new PressException(
                    "Underlying cache implementation could not store compressed file " + filePath
                            + " in cache");
        }

        long totalTime = System.currentTimeMillis() - startTime;
        PressLogger.trace("Saved file to cache in %d milli-seconds", totalTime);
    }

    public static int clearMemoryCache(String extension) {
        List<String> files = getFileList();
        for (String filePath : files) {
            Cache.delete(getCacheKey(filePath));
            Cache.delete(getInProgressKey(filePath));
        }
        Cache.delete(FILE_LIST_KEY);
        return files.size();
    }

    private static List<String> getFileList() {
        List<String> fileList = (List<String>) Cache.get(FILE_LIST_KEY);
        if (fileList == null) {
            fileList = new ArrayList<String>();
        }
        return fileList;
    }

    @Override
    public long length() {
        if (!exists()) {
            throw new PressException("Can't get length. File with key " + getCacheKey()
                    + " does not exist in cache");
        }

        return bytes.length;
    }
}
