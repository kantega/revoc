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
import org.kantega.revoc.source.SourceSource;

/**
 *
 */
public class TxtReport {

    public void run(CoverageData coverageData, SourceSource sourceSource) {
        System.out.println();
        System.out.println("========= <COVERAGE REPORT> =========");
        String[] classNames = coverageData.getClassNames();
        for (int i = 0; i < classNames.length; i++) {
            printClassCoverage(coverageData, sourceSource, i);
        }

        System.out.println("========= </COVERAGE REPORT> =========");
    }

    private  void printClassCoverage(CoverageData coverageData, SourceSource sourceSource, int i) {
        System.out.println();

        String className = coverageData.getClassNames()[i];
        System.out.println("Class " + className.replace('/', '.') + ": ");

        long[] linesVisited = coverageData.getLinesVisited(i);
        String[] sourceLines = sourceSource.getSource(className, coverageData.getClassLoader(i));
        if (sourceLines == null) {
            System.out.println("Found no source for class " + className);
            /*
            for (int l = 0; l < linesVisited.length; l++) {
                System.out.print(pad(l+1, 3) + " ");
                int numvisits = linesVisited[l];
                if (numvisits < 0) {
                    System.out.println("   ");
                } else if (numvisits == 0) {
                    System.out.println("  X");
                } else {
                    System.out.println(pad(numvisits, 3));
                }

            }*/
        } else {

            for (int l = 0; l < sourceLines.length; l++) {
                System.out.print(pad(l+1, 3) + " ");
                if (l >= linesVisited.length) {
                    System.out.print("     ");
                } else {
                    long numvisits = linesVisited[l];
                    if (numvisits < 0) {
                        System.out.print("  ");
                    } else if (numvisits == 0) {
                        System.out.print(" X");
                    } else {
                        System.out.print(pad(numvisits, 2));
                    }
                    System.out.print(" ");

                    if(isSingleEvaluatedBranchPointOnLine(coverageData, i, l)) {
                        System.out.print("C");
                    } else {
                        System.out.print(" ");
                    }
                    System.out.print(" ");

                }

                System.out.print("| ");

                System.out.print(sourceLines[l]);
                System.out.println();
            }
        }

        System.out.println("Branch points: ");
        BranchPoint[] branchPoints = coverageData.getBranchPoints(i);
        for(int b = 0; b < branchPoints.length; b++) {
            BranchPoint branchPoint = branchPoints[b];
            long trues = branchPoint.getAfter();
            long falses = branchPoint.getBefore() - branchPoint.getAfter();
            System.out.print(branchPoint.getInstruction() + " at line " + branchPoint.getLinenumber() + " " + trues + "/" + falses);
            if (trues == 0 && falses != 0 ) {
                System.out.print(" CONDITIONAL EVALUATED ONLY FALSE! ");
            } else if (falses == 0 && trues != 0) {
                System.out.print(" CONDITIONAL EVALUATED ONLY TRUE! ");
            }
            System.out.println();
        }
        System.out.println();
    }

    private boolean isSingleEvaluatedBranchPointOnLine(CoverageData coverageData, int classId, int linenumber) {
        for(BranchPoint b : coverageData.getBranchPointsForLine(classId, linenumber)) {
            if(b.isNeverBranched() || b.isAlwaysBranched()) {
                return true;
            }
        }
        return false;
    }



    private static String pad(long num, int length) {
        String pad = Long.toString(num);
        while (pad.length() < length) {
            pad = " " + pad;
        }
        return pad;
    }
}
