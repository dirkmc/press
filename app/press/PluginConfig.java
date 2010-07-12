package press;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import play.Play;
import play.Play.Mode;
import play.vfs.VirtualFile;

public class PluginConfig {
    /**
     * Stores the default configuration for the plugin
     */
    public static class DefaultConfig {
        // Whether the plugin is enabled
        public static final boolean enabled = false;

        // The caching strategy
        public static final CachingStrategy cache = (Play.mode == Mode.DEV) ? CachingStrategy.Change
                : CachingStrategy.Always;

        // Whether the cache can be cleared through the web interface
        // Default is to be available in dev only
        public static final boolean cacheClearEnabled = (Play.mode == Mode.DEV);

        // The amount of time that a compression key is stored for.
        // This only needs to be as long as the time between when the action
        // finishes and the browser requests the compressed javascript (usually
        // less than a second)
        public static final String compressionKeyStorageTime = "2mn";

        public static class js {
            // The directory where source javascript files are read from
            public static final String srcDir = "/public/javascripts/";

            // The directory where compressed javascript files are written to
            public static final String compressedDir = "/public/javascripts/press/";

            // Options for YUI JS compression
            public static final int lineBreak = -1;
            public static final boolean munge = true;
            public static final boolean warn = false;
            public static final boolean preserveAllSemiColons = false;
            public static final boolean preserveStringLiterals = false;
        }

        public static class css {
            // The directory where source css files are read from
            public static final String srcDir = "/public/stylesheets/";

            // The directory where compressed css files are written to
            public static final String compressedDir = "/public/stylesheets/press/";

            // Options for YUI CSS compression
            public static final int lineBreak = -1;
        }
    }

    public static boolean enabled;
    public static CachingStrategy cache;
    public static boolean cacheClearEnabled;
    public static String compressionKeyStorageTime;

    public static class js {
        public static String srcDir = DefaultConfig.js.srcDir;
        public static String compressedDir = DefaultConfig.js.compressedDir;

        // YUI JS compression options
        public static int lineBreak = DefaultConfig.js.lineBreak;
        public static boolean munge = DefaultConfig.js.munge;
        public static boolean warn = DefaultConfig.js.warn;
        public static boolean preserveAllSemiColons = DefaultConfig.js.preserveAllSemiColons;
        public static boolean preserveStringLiterals = DefaultConfig.js.preserveStringLiterals;
    }

    public static class css {
        public static String srcDir = DefaultConfig.css.srcDir;
        public static String compressedDir = DefaultConfig.css.compressedDir;
        public static int lineBreak = DefaultConfig.css.lineBreak;
    }

    // The unix time at which the config file was last modified
    // Note: This will be reset each time the plugin is reloaded, which is why
    // we need to call initConfLastModified() in readConfig().
    // readConfig() is called whenever the application restarts.
    static long configLastModified = 0;

    /**
     * Initializes the config file last modified timestamp.
     */
    public static void initConfLastModified() {
        VirtualFile conf = Play.getVirtualFile("conf/press.conf");

        if (conf == null || !conf.exists()) {
            configLastModified = 0;
        } else {
            configLastModified = conf.lastModified();
        }
    }

    /**
     * Checks the last modified date on the config file to see if it has changed
     * since the last time this method was called.
     */
    public static boolean hasChanged() {
        VirtualFile conf = Play.getVirtualFile("conf/press.conf");

        if (conf == null || !conf.exists()) {
            // Detect if there was a file but it has now been deleted
            return (configLastModified != 0);
        }

        long lastModified = conf.lastModified();
        return (lastModified > configLastModified);
    }

    /**
     * Reads from the config file into memory. If the config file doesn't exist
     * or is deleted, uses the default values.
     */
    public static void readConfig() {
        PressLogger.trace("Loading Press plugin configuration");

        enabled = DefaultConfig.enabled;
        cache = DefaultConfig.cache;
        cacheClearEnabled = DefaultConfig.cacheClearEnabled;
        compressionKeyStorageTime = DefaultConfig.compressionKeyStorageTime;

        css.srcDir = DefaultConfig.css.srcDir;
        css.compressedDir = DefaultConfig.css.compressedDir;

        js.srcDir = DefaultConfig.js.srcDir;
        js.compressedDir = DefaultConfig.js.compressedDir;

        // YUI options
        css.lineBreak = DefaultConfig.css.lineBreak;
        js.lineBreak = DefaultConfig.js.lineBreak;
        js.munge = DefaultConfig.js.munge;
        js.warn = DefaultConfig.js.warn;
        js.preserveAllSemiColons = DefaultConfig.js.preserveAllSemiColons;
        js.preserveStringLiterals = DefaultConfig.js.preserveStringLiterals;

        Configuration config = null;
        try {
            config = new PropertiesConfiguration("press.conf");
        } catch (ConfigurationException e) {
        }

        if (config == null) {
            PressLogger.trace("Config file not found. Using default configuration values.");
        } else {
            String cacheDefault = DefaultConfig.cache.toString();
            cache = CachingStrategy.parse(config.getString("press.cache", cacheDefault));
            cacheClearEnabled = config.getBoolean("press.cache.clearEnabled",
                    DefaultConfig.cacheClearEnabled);
            enabled = config.getBoolean("press.enabled", DefaultConfig.enabled);
            compressionKeyStorageTime = config.getString("press.key.lifetime",
                    DefaultConfig.compressionKeyStorageTime);

            css.srcDir = config.getString("press.css.sourceDir", DefaultConfig.css.srcDir);
            css.compressedDir = config.getString("press.css.outputDir",
                    DefaultConfig.css.compressedDir);

            js.srcDir = config.getString("press.js.sourceDir", DefaultConfig.js.srcDir);
            js.compressedDir = config.getString("press.js.outputDir",
                    DefaultConfig.js.compressedDir);

            // YUI options
            css.lineBreak = config.getInt("press.yui.css.lineBreak", DefaultConfig.css.lineBreak);
            js.lineBreak = config.getInt("press.yui.js.lineBreak", DefaultConfig.js.lineBreak);
            js.munge = config.getBoolean("press.yui.js.munge", DefaultConfig.js.munge);
            js.warn = config.getBoolean("press.yui.js.warn", DefaultConfig.js.warn);
            js.preserveAllSemiColons = config.getBoolean("press.yui.js.preserveAllSemiColons",
                    DefaultConfig.js.preserveAllSemiColons);
            js.preserveStringLiterals = config.getBoolean("press.yui.js.preserveStringLiterals",
                    DefaultConfig.js.preserveStringLiterals);
        }

        // Add a trailing slash to directories, if necessary
        css.srcDir = addTrailingSlash(css.srcDir);
        css.compressedDir = addTrailingSlash(css.compressedDir);
        js.srcDir = addTrailingSlash(js.srcDir);
        js.compressedDir = addTrailingSlash(js.compressedDir);

        // Log the newly loaded configuration
        logConfig();

        // Reinitialize the last modified time each time readConfig is called
        // (readConfig is called whenever the application restarts)
        initConfLastModified();
    }

    private static void logConfig() {
        PressLogger.trace("enabled: %s", enabled);
        PressLogger.trace("caching strategy: %s", cache);
        PressLogger.trace("cache publicly clearable: %s", cacheClearEnabled);
        PressLogger.trace("compression key storage time: %s", compressionKeyStorageTime);
        PressLogger.trace("css source directory: %s", css.srcDir);
        PressLogger.trace("css compressed output directory: %s", css.compressedDir);
        PressLogger.trace("js source directory: %s", js.srcDir);
        PressLogger.trace("js compressed output directory: %s", js.compressedDir);
        PressLogger.trace("YUI css line break: %d", css.lineBreak);
        PressLogger.trace("YUI js line break: %d", js.lineBreak);
        PressLogger.trace("YUI js munge: %s", js.munge);
        PressLogger.trace("YUI js warn: %s", js.warn);
        PressLogger.trace("YUI js preserve all semi colons: %s", js.preserveAllSemiColons);
        PressLogger.trace("YUI js preserve string literals: %s", js.preserveStringLiterals);
    }

    public static String addTrailingSlash(String dir) {
        if (dir.charAt(dir.length() - 1) != '/') {
            return dir + '/';
        }

        return dir;
    }
}
