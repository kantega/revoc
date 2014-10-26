package no.kantega.labs.revoc.logging;


import no.kantega.labs.revoc.config.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 *
 */
public final class LogFactory {
    private LogFactory() {}

    private static final Logger logger = configureLogger();

    private static Logger configureLogger() {
        String file = Config.getProperty("REVOC_LOG_FILE");
        if(file != null) {
            File f = new File(file);

            return new FileLogger(f);

        }

        return new ConsoleLogger();
    }

    public static Logger getLogger(Class clazz) {
        return logger;
    }

    private static class ConsoleLogger implements Logger {
        @Override
        public void info(String message) {
            System.out.println("[revoc] [info] " + message);
        }

        @Override
        public void error(String message) {
            System.err.println("[revoc] [error] " + message);
        }

        @Override
        public void error(String message, Throwable thrown) {
            System.err.println("[revoc] [error] " + message);
            thrown.printStackTrace(System.err);
        }
    }

    private static class FileLogger implements Logger {
        private final PrintStream out;

        public FileLogger(File file) {
            try {
                this.out = new PrintStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void error(String message) {
            out.print("[ERROR] ");
            out.println(message);
        }

        @Override
        public void info(String message) {
            out.print("[INFO] ");
            out.println(message);
        }

        @Override
        public void error(String message, Throwable thrown) {
            out.println("[ERROR] " + message);
            thrown.printStackTrace(out);
        }
    }
}
