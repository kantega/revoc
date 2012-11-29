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

package no.kantega.labs.revoc.web;

import no.kantega.labs.revoc.registry.BranchPoint;
import no.kantega.labs.revoc.registry.CoverageData;
import no.kantega.labs.revoc.registry.Registry;
import no.kantega.labs.revoc.report.CoverageFolder;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 */
public class JsonHandler {
    public void writeJson(CoverageData coverageData, PrintWriter writer) {
        writeJson(coverageData, writer, null);
    }
    public void writeJson(CoverageData coverageData, PrintWriter writer, BitSet forClasses) {

        if(forClasses != null) {
            coverageData = new CoverageFolder().filter(coverageData, forClasses);
        }
        /*
        System.out.println(coverageData.getClassNames().length + " changed:");
        for (String className : coverageData.getClassNames()) {
            System.out.println("\tClassname: " + className);
        }
        */
        coverageData = new CoverageFolder().fold(coverageData);

        writer.println("{loaders: " + printClassLoaderList() +", ");
        writer.println("classes: {");
        {

            final String[] classNames = coverageData.getClassNames();
            final int[] classLoaders = coverageData.getClassLoaders();
            for (int i = 0; i < classNames.length; i++) {
                if(i > 0) {
                    writer.println();
                    writer.print(",");
                }
                String className = classNames[i];
                writer.print("\"" +classLoaders[i] + "+" +className +"\":[");
                // Class loader
                writer.print(classLoaders[i]);

                writer.print(",");

                final long[] visits = coverageData.getLinesVisited(i);
                if(true) {
                    // Lines
                    writer.print("[");
                    int c  = 0;
                    for (int j = 0; j < visits.length; j++) {
                        long visit = visits[j];
                        if(visit >= 0) {
                            if(c++ > 0) {
                                writer.print(",");
                            }
                            writer.print(j);
                        }
                    }
                    writer.print("]");
                }
                // Line visits
                {
                    writer.print(",[");
                    int c = 0;
                    for (int j = 0; j < visits.length; j++) {
                        long visit = visits[j];

                        if(visit >= 0) {
                            if(c++ > 0) {
                                writer.print(",");
                            }
                            writer.print(visit);
                        }
                    }
                    writer.print("]");

                }
                // Branch points
                if (false) {
                    writer.print(",[");
                    final BranchPoint[] branchPoints = coverageData.getBranchPoints(i);
                    for (int b = 0; b < branchPoints.length; b++) {
                        BranchPoint branchPoint = branchPoints[b];
                        if(b > 0) {
                            writer.print(",");
                        }
                        writer.print("[" +branchPoint.getLinenumber() +"," + branchPoint.getBefore() +"," + branchPoint.getAfter() +"]");
                    }
                    writer.print("]");
                }

                // Last visits
                if(true){
                    final long[] times = coverageData.getLinesVisitTimes(i);
                    long last = 0;
                    for (long time : times) {
                        last = Math.max(time, last);
                    }
                    writer.print("," +last);

                    writer.print(",[");

                    int c = 0;
                    for (int j = 0; j < visits.length; j++) {
                        long t = times[j];
                        long visit = visits[j];
                        if(visit >= 0) {
                            if(c++ > 0) {
                                writer.print(",");
                            }
                            writer.print(visit == 0 ? -1 : last-t);
                        }


                    }
                    writer.print("]");

                }

                writer.print("]");
            }

        }

        writer.println();
        writer.println("}");
        writer.println("}");
    }

    private String printClassLoaderList() {
        Iterator<Registry.NamedClassLoader> classLoaders = Registry.getClassLoaders().iterator();

        StringBuilder sb = new StringBuilder();

        sb.append("[");
        while (classLoaders.hasNext()) {
            Registry.NamedClassLoader loader = classLoaders.next();
            sb.append(System.identityHashCode(loader)).append(",").append("\"").append(loader.getName()).append("\"");
            if(classLoaders.hasNext()) {
                sb.append(",");
            }

        }
        sb.append("]");

        return sb.toString();
    }

    public void writeCallTreeJson(Collection<Registry.Frame> frames, PrintWriter pw) {
        pw.println("{");
        final CoverageData coverageData = Registry.getCoverageData();
        final String[] classNames = coverageData.getClassNames();
        final String[][] methodNames = coverageData.getMethodNames();
        final String[][] methodDescriptions = coverageData.getMethodDescriptions();
        {
            pw.print("classNames: [");

            for(int i = 0; i < classNames.length; i++) {
                if(i > 0) {
                    pw.print(",");
                }
                pw.print("\"" + classNames[i] +"\"");
            }
            pw.println("],");
        }
        {
            pw.print("methods: [");
            for(int i = 0; i < classNames.length; i++) {
                if(i > 0) {
                    pw.print(",");
                }
                pw.print("[");
                for(int m = 0; m < methodNames[i].length; m++) {
                    if(m > 0) {
                        pw.print(",");
                    }
                    pw.print("\"" + methodNames[i][m] +"\"");
                }
                pw.print("]");

            }
            pw.println("],");
        }
        {
            pw.print("frames: ");

            printFrameList(frames, pw);
        }
        pw.println("}");
    }

    private void printFrameList(Collection<Registry.Frame> frames, PrintWriter pw) {
        pw.print("[");

        boolean first = true;
        for(Registry.Frame frame : frames) {
            Registry.FrameData data = frame.getData();

            if(!first) {
                pw.print(",");
            }
            first = false;
            pw.print("["
                    + frame.getClassId() +","
                    + frame.getMethodIndex() +","
                    + TimeUnit.NANOSECONDS.toMicros(data.getTime()) +","
                    + data.getVisits() +",");
            printFrameList(frame.getChildren(), pw);
            pw.print("]");

        }
        pw.print("]");
    }
}
