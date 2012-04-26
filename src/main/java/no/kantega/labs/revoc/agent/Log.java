package no.kantega.labs.revoc.agent;

import java.io.PrintStream;

/**
 *
 */
public class Log {
    public static void log(String msg) {
        msg(msg, System.out);
    }

    public static void err(String msg) {
        msg(msg, System.err);
    }

    private static void msg(String msg, PrintStream stream) {
        stream.print("[revoc] ");
        stream.println(msg);
    }
}
