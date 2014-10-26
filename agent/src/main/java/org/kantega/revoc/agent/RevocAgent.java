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

package org.kantega.revoc.agent;

import org.kantega.revoc.logging.LogFactory;
import org.kantega.revoc.logging.Logger;
import org.kantega.revoc.web.JettyStarter;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 */
public class RevocAgent {

    private static final Logger log = LogFactory.getLogger(RevocAgent.class);
    /**
     * Run when Revoc is added as a Java Agent as a startup command line option
     */
    public static void premain(String options, Instrumentation instrumentation) throws Exception {
        String[] packages = init(options);
        instrumentation.addTransformer(new RevocClassTransformer(packages));
    }


    /**
     * Run when Revoc is injected as an agent to an already running JVM
     */
    public static void agentmain(String options, Instrumentation instrumentation) throws Exception {

        String[] packages = init(options);

        retransform(instrumentation, packages);

    }

    private static String[] init(String options) throws Exception {
        log.info("Configuring coverage engine");

        Properties props = readOptions(options);

        validatePackagesConfigured(props);

        String[] packages = getPackagesToInstrument(props);

        startJettyServer(props, packages);

        return packages;
    }

    private static void retransform(Instrumentation instrumentation, String[] packages) throws UnmodifiableClassException {
        List<Class> classesToInstrument = new ArrayList<Class>();

        for (Class clazz : instrumentation.getAllLoadedClasses()) {
            if (RevocClassTransformer.shouldFilter(clazz.getClassLoader(), clazz.getName().replace('.', '/'), packages)) {
                classesToInstrument.add(clazz);
            }
        }

        log.info(String.format("Instrumenting %s classes", classesToInstrument.size()));

        instrumentation.addTransformer(new RevocClassTransformer(packages), true);

        instrumentation.retransformClasses(classesToInstrument.toArray(new Class[classesToInstrument.size()]));
    }

    private static String[] getPackagesToInstrument(Properties props) {
        String[] packages = parseList(props.getProperty("packages"));
        for (int i = 0; i < packages.length; i++) {
            packages[i] = packages[i].replace('.', '/');

        }
        return packages;
    }

    private static void validatePackagesConfigured(Properties props) {
        String packages = props.getProperty("packages");
        if (packages == null) {
            log.error("Option 'packages' must be specified. Example command line:");
            log.error("\tjava -javaagent:revoc.jar=packages=com.example.,port=7070 -jar my.jar:");
            System.exit(-1);
        } else {
            log.info("Using packages pattern(s) " + packages);
        }
    }

    private static String[] parseList(String list) {
        return list == null ? null : list.split("\\|");
    }

    private static void startJettyServer(Properties props, String[] packages) throws Exception {
        new JettyStarter().start(getPort(props.getProperty("port")), packages);
    }

    private static int getPort(String port) {
        if (port != null) {
            log.info("Using HTTP port " + port);

        } else {
            port = "7070";
        }
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            log.error("Port is not a number: " + port);
            System.exit(-1);
            return -1;
        }
    }


    private static Properties readOptions(String options) {
        Properties props = new Properties();
        if (options == null) {
            return props;
        }
        for (String abs : options.split(",")) {
            String[] ab = abs.split("=");
            props.setProperty(ab[0], ab[1]);
        }
        return props;
    }



}
