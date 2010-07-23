package press;

import play.Play;
import play.exceptions.ConfigurationException;

public class ConfigHelper {

    public static String getString(String configKey) {
        return Play.configuration.getProperty(configKey, null);
    }

    public static String getString(String configKey, String defaultValue) {
        String value = Play.configuration.getProperty(configKey);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }

        return value;
    }

    public static Boolean getBoolean(String configKey) {
        return getBoolean(configKey, null);
    }

    public static Boolean getBoolean(String configKey, Boolean defaultValue) {
        String asStr = Play.configuration.getProperty(configKey);
        if (asStr == null || asStr.length() == 0) {
            return defaultValue;
        }

        if (asStr.equals("true") || asStr.equals("false")) {
            return Boolean.parseBoolean(asStr);
        }

        throw new ConfigurationException(configKey + " must be either true or false");
    }

    public static Integer getInt(String configKey) {
        return getInt(configKey, null);
    }

    public static Integer getInt(String configKey, Integer defaultValue) {
        String asStr = Play.configuration.getProperty(configKey);
        if (asStr == null || asStr.length() == 0) {
            return defaultValue;
        }

        return Integer.parseInt(asStr);
    }
}
