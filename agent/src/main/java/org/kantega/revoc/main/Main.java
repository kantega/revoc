package org.kantega.revoc.main;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;

/**
 *
 */
public class Main {

    public static void main(String[] args) {
        File agentFile = getAgentFile();

        setup(agentFile, 7070);
    }

    public static void setup(File agentFile, int port) {

        System.out.println();
        String portString = System.console().readLine("Which port should the Revoc web server start on? [%s]: ", port);
        if(portString != null  &&  !portString.isEmpty()) {
            port = Integer.parseInt(portString);
        }

        String packages = System.console().readLine("Which packages do you want to instrument? (com.example): ", port);
        if(portString != null  &&  !portString.isEmpty()) {
            port = Integer.parseInt(portString);
        }

        String agentLine = "-javaagent:" + agentFile + "=port=" + port + ",packages=" + packages;

        System.out.println();
        System.out.println("Startup argument to enable Revoc instrumentation on port " + port + " of packages " + packages);
        System.out.println();
        System.out.println(agentLine);
        System.out.println();
        System.out.println("Using Maven?");
        System.out.println();
        System.out.println("export MAVEN_OPTS+=" + agentLine);
        System.out.println();
        System.out.println("Or Tomcat?");
        System.out.println();
        System.out.println("export CATALINA_OPTS+=" + agentLine);
        System.out.println();
    }

    private static File getAgentFile() {
        URL resource = Main.class.getResource("Main.class");
        String decoded = URLDecoder.decode(resource.getFile());
        String jarPath = decoded.substring("file:".length(), decoded.indexOf("!"));
        return new File(jarPath);
    }
}
