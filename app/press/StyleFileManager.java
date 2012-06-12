package press;

public class StyleFileManager extends SourceFileManager {
    public StyleFileManager() {
        super("CSS", StyleCompressor.EXTENSION, "#{press.stylesheet}",
                "#{press.compressed-stylesheet}", "<!-- press-css: ", " -->",
                PluginConfig.css.srcDir);
    }
}
