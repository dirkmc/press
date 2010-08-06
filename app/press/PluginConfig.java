package press;

import play.Play;
import play.Play.Mode;

public class PluginConfig {
    /**
     * Stores the default configuration for the plugin
     */
    public static class DefaultConfig {
        // Whether the plugin is enabled
        public static final boolean enabled = (Play.mode == Mode.PROD);

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

        // The maximum amount of time in milli-seconds allowed for compression
        // to occur before a timeout exception is thrown.
        public static final int maxCompressionTimeMillis = 60000;

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
    public static int maxCompressionTimeMillis;

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

    /**
     * Reads from the config file into memory. If the config file doesn't exist
     * or is deleted, uses the default values.
     */
    public static void readConfig() {
        PressLogger.trace("Loading Press plugin configuration");

        // press options
        String cacheDefault = DefaultConfig.cache.toString();
        cache = CachingStrategy.parse(ConfigHelper.getString("press.cache", cacheDefault));
        cacheClearEnabled = ConfigHelper.getBoolean("press.cache.clearEnabled",
                DefaultConfig.cacheClearEnabled);
        enabled = ConfigHelper.getBoolean("press.enabled", DefaultConfig.enabled);
        compressionKeyStorageTime = ConfigHelper.getString("press.key.lifetime",
                DefaultConfig.compressionKeyStorageTime);
        maxCompressionTimeMillis = ConfigHelper.getInt("press.compression.maxTimeMillis",
                DefaultConfig.maxCompressionTimeMillis);

        css.srcDir = ConfigHelper.getString("press.css.sourceDir", DefaultConfig.css.srcDir);
        css.compressedDir = ConfigHelper.getString("press.css.outputDir",
                DefaultConfig.css.compressedDir);

        js.srcDir = ConfigHelper.getString("press.js.sourceDir", DefaultConfig.js.srcDir);
        js.compressedDir = ConfigHelper.getString("press.js.outputDir",
                DefaultConfig.js.compressedDir);

        // YUI options
        css.lineBreak = ConfigHelper.getInt("press.yui.css.lineBreak", DefaultConfig.css.lineBreak);
        js.lineBreak = ConfigHelper.getInt("press.yui.js.lineBreak", DefaultConfig.js.lineBreak);
        js.munge = ConfigHelper.getBoolean("press.yui.js.munge", DefaultConfig.js.munge);
        js.warn = ConfigHelper.getBoolean("press.yui.js.warn", DefaultConfig.js.warn);
        js.preserveAllSemiColons = ConfigHelper.getBoolean("press.yui.js.preserveAllSemiColons",
                DefaultConfig.js.preserveAllSemiColons);
        js.preserveStringLiterals = ConfigHelper.getBoolean("press.yui.js.preserveStringLiterals",
                DefaultConfig.js.preserveStringLiterals);

        // Add a trailing slash to directories, if necessary
        css.srcDir = addTrailingSlash(css.srcDir);
        css.compressedDir = addTrailingSlash(css.compressedDir);
        js.srcDir = addTrailingSlash(js.srcDir);
        js.compressedDir = addTrailingSlash(js.compressedDir);

        // Log the newly loaded configuration
        logConfig();
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
