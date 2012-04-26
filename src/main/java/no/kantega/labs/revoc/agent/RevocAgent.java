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

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static no.kantega.labs.revoc.agent.Log.err;
import static no.kantega.labs.revoc.agent.Log.log;

/**
 *
 */
public class RevocAgent {

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
        log("Configuring coverage engine");

        Properties props = readOptions(options);

        validatePackagesConfigured(props);

        String[] packages = getPackagesToInstrument(props);

        startJettyServer(props);

        return packages;
    }

    private static void retransform(Instrumentation instrumentation, String[] packages) throws UnmodifiableClassException {
        List<Class> classesToInstrument = new ArrayList<Class>();

        for (Class clazz : instrumentation.getAllLoadedClasses()) {
            if (RevocClassTransformer.shouldFilter(clazz.getClassLoader(), clazz.getName().replace('.', '/'), packages)) {
                classesToInstrument.add(clazz);
            }
        }

        log(String.format("Instrumenting %s classes", classesToInstrument.size()));

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
            err("Option 'packages' must be specified. Example command line:");
            err("\tjava -javaagent:revoc.jar=packages=com.example.,port=7070 -jar my.jar:");
            System.exit(-1);
        } else {

            log("[revoc] Using packages pattern(s) " + packages);
        }
    }

    private static String[] parseList(String list) {
        return list == null ? null : list.split("\\|");
    }

    private static void startJettyServer(Properties props) throws Exception {
        new JettyStarter().start(props);
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
