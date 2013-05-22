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

import no.kantega.labs.revoc.instrumentation.CoverageClassVisitor;
import no.kantega.labs.revoc.logging.LogFactory;
import no.kantega.labs.revoc.logging.Logger;
import no.kantega.labs.revoc.registry.Registry;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;

/**
 *
 */
public class RevocClassTransformer implements ClassFileTransformer {

    private static Logger log = LogFactory.getLogger(RevocClassTransformer.class);

    private final String[] packages;

    public RevocClassTransformer(String[] packages) {
        this.packages = packages;
        if (packages != null) {
            for (int i = 0; i < packages.length; i++) {
                this.packages[i] = packages[i].replace('.', '/');
            }
        }
    }

    public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException {

        if (!shouldFilter(classLoader, className, packages)) {
            return null;
        } else {
            return instrumentClass(className, classFileBuffer, classLoader);

        }

    }

    private byte[] instrumentClass(String className, byte[] classFileBuffer, ClassLoader classLoader) {
        byte[] returnBytes = null;
        log.info("Instrumenting class " + className);
        ClassReader cr = new ClassReader(classFileBuffer);

        int classId = Registry.newClassId(className, classLoader);
        ClassWriter classWriter = new ClassLoaderAwareClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {};
        CoverageClassVisitor visitor = new CoverageClassVisitor(classWriter, classId);
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {

            Thread.currentThread().setContextClassLoader(classLoader);
            cr.accept(visitor, ClassReader.EXPAND_FRAMES);

        } catch (Exception e) {
            log.error("Exception instrumenting class " + className + " from classLoader " + classLoader, e);
        } finally {
            if(old != null) {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
        int[] lines = visitor.getLineIndexes();
        if(visitor.isInterface()) {
            log.info("Ignoring interface " + className);
        } else if (Registry.isClassRegistered(className, classLoader) ) {
            log.info("Instrumenting already registered class " + className);
            returnBytes = classWriter.toByteArray();
        } else if ( lines.length > 0 && visitor.getSource() != null) {
            Registry.registerClass(className, classLoader, visitor.getSource());
            Registry.registerLines(classId, lines);
            Registry.registerBranchPoints(classId, visitor.getBranchPoints());
            Registry.registerMethods(classId, visitor.getMethodNames(), visitor.getMethodDescs(), visitor.getMethodLineNumbers());
            returnBytes = classWriter.toByteArray();
        } else {
            log.info("Ignoring non-debug class " + className);
        }

        analyzeInnerClasses(visitor.getInnerClasses(), classLoader, className);
        return returnBytes;
    }

    private void analyzeInnerClasses(List<String> innerClasses, ClassLoader classLoader, String className) {
        for(String name : innerClasses) {
            if(!name.equals(className)
                    && name.startsWith(className)
                    && !Registry.isClassRegistered(name, classLoader)
                    && shouldFilter(classLoader, name, packages)) {
                try {
                    final byte[] bytes = IOUtils.toByteArray(classLoader.getResourceAsStream(name + ".class"));
                    instrumentClass(name, bytes, classLoader);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }


        }
    }

    public static boolean shouldFilter(ClassLoader classLoader, String className, String[] packages) {
        if (classLoader == null) {
            return false;
        }
        if (className.startsWith("no/kantega/labs/revoc/")) {
            return false;
        }
        if (className.startsWith("com/sun") ||
                className.startsWith("java/") ||
                className.startsWith("javax/") ||
                className.startsWith("sun/")) {
            return false;
        }

        if (packages != null) {
            for (String prefix : packages) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private class ClassLoaderAwareClassWriter extends ClassWriter {
        public ClassLoaderAwareClassWriter(ClassReader cr, int computeFrames) {
            super(cr, computeFrames);
        }

        protected String getCommonSuperClass(final String type1, final String type2)
        {
            Class<?> c, d;
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                c = Class.forName(type1.replace('/', '.'), false, classLoader);
                d = Class.forName(type2.replace('/', '.'), false, classLoader);
            } catch (Exception e) {
                throw new RuntimeException(e.toString());
            }
            if (c.isAssignableFrom(d)) {
                return type1;
            }
            if (d.isAssignableFrom(c)) {
                return type2;
            }
            if (c.isInterface() || d.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        }
    }
}
