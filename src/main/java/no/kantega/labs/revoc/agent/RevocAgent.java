/*
 * Copyright 2012 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.kantega.labs.revoc.agent;

import no.kantega.labs.revoc.source.CompondSourceSource;
import no.kantega.labs.revoc.source.MavenProjectSourceSource;
import no.kantega.labs.revoc.source.MavenSourceArtifactSourceSource;
import no.kantega.labs.revoc.web.RevocWebSocketServlet;
import no.kantega.labs.revoc.web.WebHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 */
public class RevocAgent {

    public static void agentmain(String options, Instrumentation instrumentation) throws Exception {

        System.out.println("[revoc] Configuring coverage engine");

        Properties props = readOptions(options);
        validateInclude(props);

        String[] includes = getIncludes(props);

        List<Class> classesToInstrument = new ArrayList<Class>();

        for(Class clazz : instrumentation.getAllLoadedClasses()) {
            if(RevocClassTransformer.shouldFilter(clazz.getClassLoader(), clazz.getName().replace('.','/'), includes)) {
                classesToInstrument.add(clazz);
            }
        }

        System.out.println("[revoc] Instrumenting " + classesToInstrument.size() +" classes");

        instrumentation.addTransformer(new RevocClassTransformer(includes), true);

        instrumentation.retransformClasses(classesToInstrument.toArray(new Class[classesToInstrument.size()]));





        startJettyServer(props);

    }

    private static void validateInclude(Properties props) {
        if(!props.containsKey("include")) {
            System.err.println("[revoc] JVM agent: 'include' option must be specified. Example command line:");
            System.err.println("[revoc] \tjava -javaagent:revoc.jar=include=com.example.,port=7070 -jar my.jar:");
            System.exit(-1);
        } else {
            System.out.println("[revoc] Using include pattern(s) " + props.get("include"));
        }
    }

    private static String[] getIncludes(Properties props) {
        String[] includes = parseList(props.getProperty("include"));
        for (int i = 0; i < includes.length; i++) {
            includes[i] = includes[i].replace('.','/');

        }
        return includes;
    }

    public static void premain(String options, Instrumentation instrumentation) throws Exception {
        Properties props = readOptions(options);
        validateInclude(props);
        String[] includes = parseList(props.getProperty("include"));
        instrumentation.addTransformer(new RevocClassTransformer(includes));
        startJettyServer(props);
    }

    private static String[] parseList(String list) {
        return list == null ? null : list.split("\\|");
    }

    private static Server startJettyServer(Properties props) throws Exception {
        String port = props.getProperty("port");
        if(port != null) {
            System.out.println("[revoc] Using HTTP port " + port);

        } else {
            port = "7070";
        }
        int p = 0;
        try {
            p = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            System.err.println("[revoc] Port is not a number: " + port);
            System.exit(-1);
        }

        Server server = new Server(p);

        HandlerList collection = new HandlerList();

        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/ws");
        ctx.addServlet(RevocWebSocketServlet.class, "/ws");
        collection.addHandler(ctx);

        collection.addHandler(new WebHandler(new CompondSourceSource(new MavenProjectSourceSource(), new MavenSourceArtifactSourceSource())));


        server.setHandler(collection);

        server.start();
        return server;
    }

    private static Properties readOptions(String options) {
        Properties props = new Properties();
        if(options == null) {
            return props;
        }
        for(String abs : options.split(",")) {
            String[] ab = abs.split("=");
            props.setProperty(ab[0], ab[1]);
        }
        return props;
    }
}
