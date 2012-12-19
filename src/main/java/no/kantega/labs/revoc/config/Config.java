package no.kantega.labs.revoc.config;

/**
 *
 */
public abstract class Config {

    private Config() {}

    public static String getProperty(String key) {
        String val = System.getenv(key);
        if(val != null) {
            return val;
        } else {
            return System.getProperty(key);
        }
    }
}
