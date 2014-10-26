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

package no.kantega.labs.revoc.demo;

import no.kantega.labs.revoc.instrumentation.CoverageClassVisitor;
import no.kantega.labs.revoc.registry.Registry;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

/**
 *
 */
public class LambdasTest {

    @Test
    public void shouldRegisterVisitedLine() throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        Registry.resetRegistry();
        final Class<Lambdas> clazz = Lambdas.class;
        InputStream inputStream = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");

        ClassReader cr = new ClassReader(inputStream);

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        int classId = Registry.newClassId(clazz.getName().replace('.', '/'), clazz.getClassLoader());
        CoverageClassVisitor visitor = new CoverageClassVisitor(new TraceClassVisitor(cw,new PrintWriter(System.out)), classId);
        cr.accept(visitor, ClassReader.EXPAND_FRAMES);
        Registry.registerClass(clazz.getClass().getName(), clazz.getClassLoader(), visitor.getSource());
        Registry.registerLines(classId, visitor.getLineIndexes());
        Registry.registerMethods(classId, visitor.getMethodNames(), visitor.getMethodDescs(), visitor.getMethodLineNumbers());

        ClassUtils.invokeMainMethodUsingReflection(clazz.getName(), cw.toByteArray(), 1);

        // Then

        long[] linesVisited = Registry.getCoverageData().getLinesVisited(classId);
        Lines lines = new Lines(classId);
        lines.once(26);
    }

}
