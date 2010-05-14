package press;

import play.Logger;

public class PressLogger {
    public static void trace(String message, Object... args) {
        Logger.trace("Press: " + message, args);
    }
}
