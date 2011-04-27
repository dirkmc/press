package press.io;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import play.Play;

/**
 * @author jareware
 */
public class PressFileGlobber {
    static Pattern pattern = Pattern.compile("(?:.*/)?(\\*\\*?)\\.(\\w+)");

    /**
     * Resolves a potentially globbed filename to a list of filenames:
     * 
     * @example getResolvedFiles("my-app/*.js"); // => { "my-app/a.js",
     *          "my-app/b.js" }
     * 
     *          Non-globbed paths are returned as-is:
     * 
     * @example getResolvedFiles("my-app/foo.css"); // => { "my-app/foo.css" }
     * 
     *          Note that patterns with partial filenames ("foo*.js") aren't
     *          supported. Filenames ending in "**.js" are treated recursively.
     * 
     *          The fileName is expected to be in the same form as with addJS()
     *          (that is, excluding the press.js.sourceDir part etc). The same
     *          goes for the returned paths.
     * 
     * @param fileName
     *            filename as given in template
     * @param sourceDir
     *            filename prefix as given in configuration
     * @return
     */
    @SuppressWarnings("unchecked")
    public static List<String> getResolvedFiles(String fileName, String sourceDir) {
        List<String> sources = new ArrayList<String>();

        Matcher m = pattern.matcher(fileName);
        if (!m.matches()) {
            sources.add(fileName);
            return sources;
        }

        String extension = m.group(2);
        boolean isRecursive = m.group(1).length() == 2;

        fileName = fileName.substring(0, fileName.length() - extension.length()
                - (isRecursive ? 3 : 2));

        String fullPath = Play.applicationPath.getAbsolutePath() + sourceDir;
        String[] extensionFilter = { extension };
        File startLookingFrom = new File(fullPath + fileName);
        Collection<File> files = FileUtils
                .listFiles(startLookingFrom, extensionFilter, isRecursive);

        for (File file : files) {
            String relativePath = file.getAbsolutePath().substring(fullPath.length());
            sources.add(relativePath);
        }

        // sort by US ASCII by default
        Collections.sort(sources, Collator.getInstance(Locale.US));

        return sources;
    }

}
