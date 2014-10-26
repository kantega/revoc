package org.kantega.revoc.logging;

/**
 *
 */
public interface Logger {
    void info(String message);

    void error(String message, Throwable thrown);

    void error(String message);
}
