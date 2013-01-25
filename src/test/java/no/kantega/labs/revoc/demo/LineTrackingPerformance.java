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

import no.kantega.labs.helloworld.LongLoop;
import no.kantega.labs.revoc.instrumentation.CoverageClassVisitor;
import no.kantega.labs.revoc.registry.CoverageData;
import no.kantega.labs.revoc.registry.Registry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import static no.kantega.labs.revoc.demo.ClassUtils.invokeMainMethodUsingReflection;

/**
 *
 */
public class LineTrackingPerformance {

    public static void main(String[] args) throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

        Class clazz = LongLoop.class;
        InputStream stream = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");

        ClassReader cr = new ClassReader(stream);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        TraceClassVisitor trace = new TraceClassVisitor(writer, new PrintWriter(System.out));
        CoverageClassVisitor visitor = new CoverageClassVisitor(trace, 0);
        cr.accept(visitor, ClassReader.EXPAND_FRAMES);


        if (true) {

            Registry.registerClass(clazz.getName().replace('.', '/'), clazz.getClassLoader(), visitor.getSource());
            Registry.registerLines(0, visitor.getLineIndexes());
            Registry.registerBranchPoints(0, visitor.getBranchPoints());
            Registry.registerMethods(0, visitor.getMethodNames(), visitor.getMethodDescs(), visitor.getMethodLineNumbers());


            System.out.println("Untouched main class");
            for(int i = 0; i < 50; i++) {
                long before = System.currentTimeMillis();
                LongLoop.main(null);
                System.out.println(System.currentTimeMillis() - before);
            }

            System.out.println("Instrumented main class");

            invokeMainMethodUsingReflection(clazz.getName(), writer.toByteArray(), 50);


            final CoverageData coverageData = Registry.getCoverageData();
            System.out.println("L36: " + coverageData.getLinesVisited(0)[35]);
            System.out.println("T36: " + coverageData.getLinesVisitTimes(0)[35]);
            System.out.println("T42: " + coverageData.getLinesVisitTimes(0)[41]);
            System.out.println("L52: " + coverageData.getLinesVisited(0)[51]);
            System.out.println("L64: " + coverageData.getLinesVisited(0)[63]);
            System.out.println("L72: " + coverageData.getLinesVisited(0)[71]);
            System.out.println("T72: " + coverageData.getLinesVisitTimes(0)[71]);
            System.out.println("L44: " + coverageData.getLinesVisited(0)[43]);
            System.out.println("T44: " + coverageData.getLinesVisitTimes(0)[43]);

            if(false)  {
                System.out.println("B35_A: " + coverageData.getBranchPointsForLine(0, 34)[0].getAfter());
                System.out.println("B35_B: " + coverageData.getBranchPointsForLine(0, 34)[0].getBefore());

                System.out.println("B43_A: " + coverageData.getBranchPointsForLine(0, 42)[0].getAfter());
                System.out.println("B43_B: " + coverageData.getBranchPointsForLine(0, 42)[0].getBefore());
            }

            Registry.dumpFrames();
        }


    }






}
