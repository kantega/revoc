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

package org.kantega.revoc.instrumentation;

import org.kantega.helloworld.HelloWorld;
import org.kantega.revoc.instrumentation.testclasses.ClassWithLongMethod;
import org.kantega.revoc.instrumentation.testclasses.SimpleClass;
import org.kantega.revoc.instrumentation.testclasses.SyntheticClass;
import org.kantega.revoc.registry.CoverageData;
import org.kantega.revoc.registry.Registry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.kantega.revoc.demo.ClassUtils.invokeMainMethodUsingReflection;
import static org.junit.Assert.*;

/**
 *
 */
public class CoverageClassVisitorTest {
    @Before
    public void setup() {
        Registry.resetRegistry();
    }

    @Test
    public void shouldRegisterLineAndBranchPoints() throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

        final Class clazz = SimpleClass.class;

        CoverageClassVisitor visitor = new InstrumentationTemplate(clazz).run();
        int classId = visitor.getClassId();
        // Then
        final String vmClassName = clazz.getName().replace('.', '/');
        assertEquals(vmClassName, visitor.getClassName());
        assertFalse(visitor.isInterface());
        assertEquals(1, visitor.getInnerClasses().size());
        assertEquals(vmClassName + "$InnerClass", visitor.getInnerClasses().get(0));
        assertEquals(9, visitor.getExistingLines().cardinality());
        assertTrue("Expected HelloWorld to have code on line 22", visitor.getExistingLines().get(22));
        assertTrue("Expected HelloWorld to have code on line 25", visitor.getExistingLines().get(25));
        assertTrue("Expected HelloWorld to have code on line 26", visitor.getExistingLines().get(26));
        assertTrue("Expected HelloWorld to have code on line 28", visitor.getExistingLines().get(28));
        assertTrue("Expected HelloWorld to have code on line 29", visitor.getExistingLines().get(29));
        assertTrue("Expected HelloWorld to have code on line 31", visitor.getExistingLines().get(31));

        CoverageData data = Registry.getCoverageData();
        assertEquals(-1, data.getLinesVisited(classId)[20]);
        assertEquals(1, data.getLinesVisited(classId)[21]);
        assertTrue(data.getLinesVisitTimes(classId)[21] != 0);
        assertEquals(11, data.getLinesVisited(classId)[24]);
        assertEquals(10, data.getLinesVisited(classId)[25]);
        assertEquals(1, data.getLinesVisited(classId)[27]);
        assertTrue(data.getLinesVisitTimes(classId)[27] != 0);
        assertEquals(0, data.getLinesVisited(classId)[28]);
        assertEquals(0, data.getLinesVisitTimes(classId)[28]);
        assertEquals(1, data.getLinesVisited(classId)[30]);
        assertEquals(1, data.getLinesVisited(classId)[31]);
        assertEquals(data.getLinesVisitTimes(classId)[30], data.getLinesVisitTimes(classId)[31]);
    }

    @Test
    public void shouldWorkWithLargeMethod() throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

        // Want it to work with or without tracking time or branches
        for (final boolean trackTime : new boolean[]{true, false}) {
            for (final boolean trackBranches : new boolean[]{false}) {
                Registry.resetRegistry();

                final Class clazz = ClassWithLongMethod.class;

                CoverageClassVisitor visitor = new InstrumentationTemplate(clazz) {
                    protected void configureClassVisitor(CoverageClassVisitor visitor) {
                        visitor.setTrackTime(trackTime);
                        visitor.setTrackBranches(trackBranches);
                    }
                }.run();
                int classId = visitor.getClassId();
                // Then
                assertEquals(377, visitor.getExistingLines().cardinality());

                CoverageData data = Registry.getCoverageData();
                assertEquals(1, data.getLinesVisited(classId)[26]);
                assertEquals(0, data.getLinesVisited(classId)[27]);
                assertEquals(1, data.getLinesVisited(classId)[29]);
                assertEquals(0, data.getLinesVisited(classId)[30]);
                assertEquals(1, data.getLinesVisited(classId)[32]);
                assertEquals(1, data.getLinesVisited(classId)[33]);
                assertEquals(0, data.getLinesVisited(classId)[35]);
                if(trackTime) {
                    assertTrue(0 != data.getLinesVisitTimes(classId)[26]);
                    assertEquals(0, data.getLinesVisitTimes(classId)[27]);
                }
                if(trackBranches) {
                    assertEquals(1, data.getBranchPointsForLine(classId, 26).length);
                    assertEquals(0, data.getBranchPointsForLine(classId, 26)[0].getAfter());
                    assertEquals(1, data.getBranchPointsForLine(classId, 32)[0].getAfter());
                }
            }
        }
    }

    @Test
    public void syntheticMethodsShouldNotBeImplemented() {
        final Class clazz = SyntheticClass.Inner.class;

        CoverageClassVisitor visitor = new InstrumentationTemplate(clazz) {
            @Override
            protected void executeCode(ClassWriter cw, Class clazz) throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

            }
        }.run();

        // Then line 11 in the synthetic method should not be registered
        assertFalse(visitor.getExistingLines().get(11));

    }

    @Test
    @Ignore
    public void largeNumberOfClasses() {

        for(int i = 0; i <= Short.MAX_VALUE;i++) {
            int cid = Registry.registerClass("class" + i, getClass().getClassLoader(), "class" + i + ".java");
            ArrayList<String> methodNames = new ArrayList<String>();
            methodNames.add("<init>");
            methodNames.add("main");
            ArrayList<String> methodDescs = new ArrayList<String>();
            methodDescs.add("");
            methodDescs.add("");
            Registry.registerMethods(0, methodNames, methodDescs, new HashMap<Integer, List<Integer>>());
        }

        final Class clazz = SimpleClass.class;

        CoverageClassVisitor visitor = new InstrumentationTemplate(clazz).run();

        assertEquals(Short.MAX_VALUE +1, visitor.getClassId());

    }


    class InstrumentationTemplate {
        private final Class clazz;

        public InstrumentationTemplate(Class clazz) {
            this.clazz = clazz;
        }

        public CoverageClassVisitor run() {
            try {
                InputStream inputStream = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class");

                int classId = Registry.newClassId(clazz.getName().replace('.', '/'), clazz.getClassLoader());

                ClassReader cr = new ClassReader(inputStream);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                CoverageClassVisitor visitor = new CoverageClassVisitor(new TraceClassVisitor(cw,new PrintWriter(System.out)), classId);
                configureClassVisitor(visitor);
                cr.accept(visitor, ClassReader.EXPAND_FRAMES);
                registerClassInfo(classId, visitor);

                executeCode(cw, clazz);

                return visitor;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }

        }

        protected void executeCode(ClassWriter cw, Class clazz) throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
            invokeMainMethodUsingReflection(clazz.getName(), cw.toByteArray());
        }

        protected void registerClassInfo(int classId, CoverageClassVisitor visitor) {
            Registry.registerClass(HelloWorld.class.getName(), HelloWorld.class.getClassLoader(), visitor.getSource());
            Registry.registerLines(classId, visitor.getLineIndexes());
            Registry.registerBranchPoints(classId, visitor.getBranchPoints());
            Registry.registerMethods(classId, visitor.getMethodNames(), visitor.getMethodDescs(), visitor.getMethodLineNumbers());
        }

        protected void configureClassVisitor(CoverageClassVisitor visitor) {

        }
    }
}
