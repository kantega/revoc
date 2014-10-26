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

import org.kantega.revoc.instrumentation.CoverageClassVisitor;
import org.kantega.revoc.registry.Registry;
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
public class LargeNumberOfLinesTest {

    @Test
    public void shouldRegisterVisitedLine() throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        Registry.resetRegistry();
        final Class<LargeNumberOfLines> clazz = LargeNumberOfLines.class;
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

        Lines lines = new Lines(classId);
        lines.once(9);
        lines.once(72);
        lines.once(73);
        lines.once(74);
        lines.once(75);
        lines.once(77);
        lines.never(78);

    }

}
