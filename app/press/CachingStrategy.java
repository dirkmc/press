package press;

import java.util.Arrays;
import java.util.List;

import play.templates.JavaExtensions;

public enum CachingStrategy {
    Always, Never, Change;

    public static CachingStrategy parse(String name) {
        String lcName = name.trim().toLowerCase();
        for (CachingStrategy strategy : CachingStrategy.values()) {
            if (strategy.toString().toLowerCase().equals(lcName)) {
                return strategy;
            }
        }

        String msg = "Could not parse caching strategy name from '" + name + "'. ";
        List<CachingStrategy> strategies = Arrays.asList(CachingStrategy.values());
        msg += "Caching strategy must be one of " + JavaExtensions.join(strategies, ", ");
        throw new RuntimeException(msg);
    }
}
