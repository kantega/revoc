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

package org.kantega.revoc.report;

import org.kantega.revoc.registry.BranchPoint;
import org.kantega.revoc.registry.CoverageData;
import org.kantega.revoc.registry.Registry;

import java.util.*;

/**
 *
 */
public class CoverageFolder {
    public CoverageData fold(CoverageData coverageData) {
        Map<String, List<Integer>> sources = new LinkedHashMap<String, List<Integer>>();

        Map<String, Integer> topLevels = new TreeMap<String, Integer>();

        for(int i = 0; i < coverageData.getClassNames().length; i++) {
            String sourceName = coverageData.getSourceFiles()[i];
            String className = coverageData.getClassNames()[i];
            String sourcePath = className.substring(0, className.lastIndexOf("/")+1) + sourceName;


            String sourceKey = coverageData.getClassLoaders()[i] +"-" + sourcePath;


            if(!sources.containsKey(sourceKey)) {
                sources.put(sourceKey, new ArrayList<Integer>());
            }

            if(className.equals(sourcePath.substring(0, sourcePath.length() - ".java".length()))) {
                topLevels.put(sourceKey, i);
            }
            List<Integer> classes = sources.get(sourceKey);
            classes.add(i);
        }

        final long[][] linesVisited = new long[topLevels.size()][];
        final long[][] lineTimes = new long[topLevels.size()][];

        final String[] classNames = new String[topLevels.size()];
        final int[] classLoaders = new int[topLevels.size()];
        final String[] sourceFiles = new String[topLevels.size()];
        final BranchPoint[][] branchPoints = new BranchPoint[topLevels.size()][];

        int c = 0;
        for(String sourceKey : sources.keySet()) {
            if(topLevels.get(sourceKey) == null) {
                continue;
            }
            int maxLines = 0;
            int numBranchPoints = 0;
            for(Integer i : sources.get(sourceKey)) {
                maxLines = Math.max(maxLines, coverageData.getLinesVisited(i).length);
                numBranchPoints += coverageData.getBranchPoints(i).length;
            }

            branchPoints[c] = new BranchPoint[numBranchPoints];
            int b = 0;

            classNames[c] = coverageData.getClassNames()[topLevels.get(sourceKey)];
            classLoaders[c] = coverageData.getClassLoaders()[topLevels.get(sourceKey)];

            linesVisited[c] = new long[maxLines];
            Arrays.fill(linesVisited[c], -1);

            lineTimes[c] = new long[maxLines];
            Arrays.fill(lineTimes[c], -1);

            for(Integer i : sources.get(sourceKey)) {
                for(int l = 0; l < coverageData.getLinesVisited(i).length; l++) {
                    long visits = coverageData.getLinesVisited(i)[l];
                    if(visits >=0) {
                        linesVisited[c][l] = visits;
                    }

                }
                for(int l = 0; l < coverageData.getLinesVisitTimes(i).length; l++) {
                    long time = coverageData.getLinesVisitTimes(i)[l];
                    if(time >=0) {
                        lineTimes[c][l] = time;
                    }

                }
                for(BranchPoint branchPoint : coverageData.getBranchPoints(i)) {
                    branchPoints[c][b++] = branchPoint;
                }
            }

            c++;

        }
        return new CoverageData() {
            public long[] getLinesVisited(int classId) {
                return linesVisited[classId];
            }

            public long[] getLinesVisitTimes(int classId) {
                return lineTimes[classId];
            }

            public String[] getClassNames() {
                return classNames;
            }

            @Override
            public int[] getClassLoaders() {
                return classLoaders;
            }

            @Override
            public ClassLoader getClassLoader(int classId) {
                return Registry.getClassLoader(classId);
            }

            public String[] getSourceFiles() {
                return sourceFiles;
            }

            public BranchPoint[] getBranchPoints(int classId) {
                return branchPoints[classId];
            }

            public BranchPoint[] getBranchPointsForLine(int classId, int lineNumber) {
                 List<BranchPoint> points = new ArrayList<BranchPoint>();
                for (BranchPoint b : branchPoints[classId]) {
                    if (b.getLinenumber() == lineNumber) {
                        points.add(b);
                    }
                }
                return points.toArray(new BranchPoint[points.size()]);
            }

            public String[][] getMethodNames() {
                throw new IllegalStateException("Folding not implemented for method names");
            }

            public String[][] getMethodDescriptions() {
                throw new IllegalStateException("Folding not implemented for method descriptions");
            }
        };
    }

    public CoverageData filter(CoverageData coverageData, BitSet forClasses) {

        final long[][] linesVisited = new long[forClasses.cardinality()][];
        final long[][] linesVisitedTime = new long[forClasses.cardinality()][];
        final String[][] methodNames = new String[forClasses.cardinality()][];
        final String[][] methodDescr = new String[forClasses.cardinality()][];
        final String[] sourceFiles = new String[forClasses.cardinality()];
        final String[] classNames = new String[forClasses.cardinality()];
        final int[] classLoadersIds = new int[forClasses.cardinality()];

        int c = 0;
        for (int i = forClasses.nextSetBit(0); i >= 0; i = forClasses.nextSetBit(i+1)) {
            linesVisited[c] = coverageData.getLinesVisited(i);
            linesVisitedTime[c] = coverageData.getLinesVisitTimes(i);
            methodNames[c] = coverageData.getMethodNames()[i];
            methodDescr[c] = coverageData.getMethodDescriptions()[i];
            sourceFiles[c] = coverageData.getSourceFiles()[i];
            classNames[c] = coverageData.getClassNames()[i];
            classLoadersIds[c] = coverageData.getClassLoaders()[i];
            c++;
        }

        return new CoverageData() {
            @Override
            public long[] getLinesVisited(int classId) {
                return linesVisited[classId];
            }

            @Override
            public long[] getLinesVisitTimes(int classId) {
                return linesVisitedTime[classId];
            }

            @Override
            public String[] getClassNames() {
                return classNames;
            }

            @Override
            public String[][] getMethodNames() {
                return methodNames;
            }

            @Override
            public String[][] getMethodDescriptions() {
                return methodDescr;
            }

            @Override
            public String[] getSourceFiles() {
                return sourceFiles;
            }

            @Override
            public BranchPoint[] getBranchPoints(int classId) {
                return new BranchPoint[0];  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public BranchPoint[] getBranchPointsForLine(int classId, int lineNumber) {
                return new BranchPoint[0];  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public int[] getClassLoaders() {
                return classLoadersIds;
            }

            @Override
            public ClassLoader getClassLoader(int i) {
                return Registry.getClassLoader(i);
            }
        };
    }
}
