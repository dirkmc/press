package press.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;

import play.Play;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;
import press.PressException;

public class FileIO {

    /**
     * Gets the file at the given path, relative to the application root, even
     * if the file doesn't exist
     */
    public static VirtualFile getVirtualFile(String filePath) {
        VirtualFile vf = Play.getVirtualFile(filePath);
        if (vf == null) {
            return VirtualFile.open(Play.getFile(filePath));
        }
        return vf;
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

    public static void write(Reader reader, Writer writer) throws IOException {
        int read = 0;
        char[] buffer = new char[8096];
        while ((read = reader.read(buffer)) > 0) {
            writer.write(buffer, 0, read);
        }
    }

    public static Reader getReader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static String getFileNameFromPath(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < filePath.length()) {
            return filePath.substring(lastSlash + 1);
        }

        return filePath;
    }

    /**
     * Converts any non-letter characters in the given string to letters
     */
    public static String lettersOnly(String hashed) {
        char[] chars = hashed.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetter(chars[i])) {
                chars[i] = (char) ((int) 'A' + (int) chars[i] % 26);
            }
        }

        return new String(chars);
    }

    public static String escape(String url) {
        try {
            return URLEncoder.encode(url, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnexpectedException(e);
        }
    }

    public static String unescape(String url) {
        try {
            return URLDecoder.decode(url, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnexpectedException(e);
        }
    }

}
