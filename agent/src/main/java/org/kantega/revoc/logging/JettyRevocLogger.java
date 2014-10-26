package org.kantega.revoc.logging;

/**
 *
 */
public class JettyRevocLogger implements org.eclipse.jetty.util.log.Logger {
    private static Logger log = LogFactory.getLogger(JettyRevocLogger.class);

    @Override
    public void debug(String msg, Object... args) {
        log.info("[jetty] [DEBUG] " +msg);
    }

    @Override
    public String getName() {
        return JettyRevocLogger.class.getName();
    }

    @Override
    public void warn(String msg, Object... args) {
        log.info("[jetty] [WARN] " +msg);
    }

    @Override
    public void warn(Throwable thrown) {
        log.info("[jetty] [WARN] " +thrown.getMessage());
    }

    @Override
    public void warn(String msg, Throwable thrown) {
        log.info("[jetty] [WARN] " +msg);
    }

    @Override
    public void info(String msg, Object... args) {
        log.info("[jetty] [INFO] " +msg);
    }

    @Override
    public void info(Throwable thrown) {
        log.info("[jetty] [INFO] " +thrown.getMessage());
    }

    @Override
    public void info(String msg, Throwable thrown) {
        log.info("[jetty] [INFO] " +msg);
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void setDebugEnabled(boolean enabled) {

    }

    @Override
    public void debug(Throwable thrown) {
        log.info("[jetty] [DEBUG] " +thrown.getMessage());
    }

    @Override
    public void debug(String msg, Throwable thrown) {
        log.info("[jetty] [DEBUG] " +msg);
    }

    @Override
    public void debug(String msg, long value) {
        log.info("[jetty] [DEBUG] " +msg);
    }

    @Override
    public org.eclipse.jetty.util.log.Logger getLogger(String name) {
        return this;
    }

    @Override
    public void ignore(Throwable ignored) {

    }
}
