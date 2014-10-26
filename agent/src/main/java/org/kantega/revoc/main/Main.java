package org.kantega.revoc.main;

import org.kantega.revoc.web.JettyStarter;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;

/**
 *
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.equals("--port")) {
                port = Integer.parseInt(args[++i]);
            } else if(args.equals("--help")) {
                help();
            } else {
                help();
            }
        }
        File agentFile = getAgentFile();

        new JettyStarter().startSetup(agentFile, port);
        System.out.println("Done");
    }

    private static void help() {
        System.out.println("java -jar " + getAgentFile().getName() + "<options>");
        System.out.println(" Options:");
        System.out.println("  --help            Shows this help" );
        System.out.println("  --port 7070       Starts server on port 7070" );
        System.out.println();
        System.exit(0);
    }

    private static File getAgentFile() {
        URL resource = Main.class.getResource("Main.class");
        String decoded = URLDecoder.decode(resource.getFile());
        String jarPath = decoded.substring("file:".length(), decoded.indexOf("!"));
        return new File(jarPath);
    }
}
