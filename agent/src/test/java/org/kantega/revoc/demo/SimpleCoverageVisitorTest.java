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

package org.kantega.revoc.demo;

import org.kantega.helloworld.HelloWorld;
import org.kantega.revoc.analysis.OneLineAnalyze;
import org.kantega.revoc.instrumentation.CoverageClassVisitor;
import org.kantega.revoc.registry.CoverageData;
import org.kantega.revoc.registry.Registry;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SimpleCoverageVisitorTest {

    @Test
    public void shouldRegisterVisitedLine() throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

        final Class<HelloWorld> clazz = HelloWorld.class;
        InputStream inputStream = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");

        ClassReader cr = new ClassReader(inputStream);

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        int classId = Registry.newClassId(clazz.getName().replace('.', '/'), clazz.getClassLoader());
        CoverageClassVisitor visitor = new CoverageClassVisitor(cw, classId) {
            @Override
            protected MethodVisitor createSecondPassAnalyzer(int classId, Map<Integer, Integer> classLineNumbers, Map<Integer, Integer> methodLineNumbers, Map<Integer, Integer> branchPoints, int reportLoad, OneLineAnalyze oneTimeLines, MethodVisitor mv, int access, String name, String desc) {
                return new SimpleConverageVisitor(classId, classLineNumbers, mv);
            }
        };
        cr.accept(visitor, ClassReader.EXPAND_FRAMES);
        Registry.registerClass(clazz.getClass().getName(), clazz.getClassLoader(), visitor.getSource());
        Registry.registerLines(classId, visitor.getLineIndexes());
        Registry.registerMethods(classId, visitor.getMethodNames(), visitor.getMethodDescs(), visitor.getMethodLineNumbers());

        ClassUtils.invokeMainMethodUsingReflection("org.kantega.helloworld.HelloWorld", cw.toByteArray(), 3);

        // Then

        CoverageData data = Registry.getCoverageData();
        assertEquals(-1, data.getLinesVisited(classId)[20]);
        assertEquals(3, data.getLinesVisited(classId)[24]);
    }
}
