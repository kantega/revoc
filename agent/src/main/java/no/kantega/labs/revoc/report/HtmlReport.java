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

package no.kantega.labs.revoc.report;

import no.kantega.labs.revoc.registry.BranchPoint;
import no.kantega.labs.revoc.registry.CoverageData;
import no.kantega.labs.revoc.source.SourceSource;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class HtmlReport {

    public void run(CoverageData coverageData, String selectedClass, Writer writer, SourceSource sourceSource) {

        long now = System.currentTimeMillis();
        coverageData = new CoverageFolder().fold(coverageData);

        PrintWriter pw = new PrintWriter(writer);

        pw.println(" <!DOCTYPE html><html>");
        pw.println("<head>");
        pw.println("<style type=\"text/css\">");
        try {
            IOUtils.copy(new InputStreamReader(HtmlReport.class.getResourceAsStream("revoc.css"), "utf-8"), writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        pw.println("</style>");
        pw.println("</head>");
        pw.println("<body>");

        pw.println("<h1>Revoc Coverage Report</h1>");

        printMenu(pw);

        pw.println("<div id=\"classlist\">");

        String[] classNames = coverageData.getClassNames();

        for (int i = 0; i < classNames.length; i++) {
            String className = coverageData.getClassNames()[i];
            if(selectedClass != null && !selectedClass.equals(className)) {
                continue;
            }

            pw.println("<h2>Class " + className.replace('/', '.') +"</h2>");

            String sourceFile = coverageData.getSourceFiles()[i];

            long[] linesVisited = coverageData.getLinesVisited(i);
            String sourceClassname = className;
            if(sourceFile != null && !(className +".java").endsWith(sourceFile)) {
                sourceClassname = className.substring(0, className.lastIndexOf("/")+1) + sourceFile.substring(0, sourceFile.length()-".java".length());
            }
            String[] sourceLines = sourceSource.getSource(sourceClassname, coverageData.getClassLoader(i));
            if (sourceLines == null) {
                pw.println("<p>Found no source for class " + className +"</p>");
            } else {
                pw.println("<table>");
                for (int l = 0; l < sourceLines.length; l++) {

                    // noline, nonvisited, visited
                    // singlebranched, branched
                    StringBuffer conditionals = new StringBuffer();
                    StringBuffer conditionalExpl = new StringBuffer();
                    List<BranchPoint> singleBranchPoints = new ArrayList<BranchPoint>();


                    // Row with classes
                    pw.print("<tr class=\"");
                    if(l >= linesVisited.length || linesVisited[l] < 0) {
                        pw.print("noline");
                    } else if(linesVisited[l] == 0) {
                        pw.print("nonvisited");
                    } else {
                        pw.print("visited");
                    }

                    if(l < linesVisited.length ) {
                        BranchPoint[] branchPointsForLine = coverageData.getBranchPointsForLine(i, l);

                        if(branchPointsForLine.length > 0) {
                            pw.print(" branched");
                        }

                        for(int b = 0; b < branchPointsForLine.length; b++) {
                            BranchPoint branchPoint = branchPointsForLine[b];
                            conditionals.append((branchPoint.getBefore()-branchPoint.getAfter())+ "/" + branchPoint.getAfter());
                            if(b < branchPointsForLine.length-1) {
                                conditionals.append(", ");
                            }
                            if(branchPoint.isNeverBranched() || branchPoint.isAlwaysBranched()) {
                                singleBranchPoints.add(branchPoint);
                            }

                        }

                        if(!singleBranchPoints.isEmpty() && linesVisited[l] != 0) {
                            pw.print(" singlebranched");
                        }

                        for(int b = 0; b < singleBranchPoints.size(); b++) {
                            BranchPoint branchPoint = singleBranchPoints.get(b);
                            conditionalExpl.append("Condition " + (b+1) +" " +(branchPoint.isNeverBranched() ? "never" : "always") +" branched. (" + (branchPoint.getBefore()-branchPoint.getAfter()) + " of " + branchPoint.getBefore() +" times)");
                            if(b < singleBranchPoints.size()-1) {
                                conditionalExpl.append("\n");
                            }
                        }

                    }

                    pw.println("\">");






                    // Line number
                    pw.println("<td class=linenumber id=\"line-" +(l+1) +"\"><a name=\"" +(l + 1) +"\">" +(l + 1) +"</a></td>");


                    // Num visits
                    pw.print("<td class=numvisits>");

                    if (l >= linesVisited.length) {

                    } else {
                        long numvisits = linesVisited[l];
                        if (numvisits == 0) {
                            pw.print("0");
                        } else if(numvisits > 0) {
                            pw.print(numvisits);
                        }
                    }
                    pw.println("</td>");

                    //

                    pw.print("<td class=\"conditional\">");
                    if(conditionals.length() > 0 && linesVisited[l] != 0) {
                        pw.print("<a title=\"" + conditionalExpl.toString() +"\">" +conditionals.toString() +"</a>");
                    }

                    pw.println("</td>");

                    String time = "";
                    if(l < linesVisited.length && coverageData.getLinesVisitTimes(i)[l] >0) {
                        final long secondsSince = (now - coverageData.getLinesVisitTimes(i)[l]) / 1000;
                        if(secondsSince <= 60) {
                            time += secondsSince +"s";
                        } else if(secondsSince < 3600) {
                            time += (secondsSince/60) +"m";
                        }  else if(secondsSince < 86400) {
                            time += (secondsSince/3600) +"h";
                        } else {
                            time += (secondsSince/86400) +"d";
                        }
                    }
                    pw.println("<td class=\"lastvisit\">" + time +"</td>");




                    pw.println("<td class=sourceline><span><pre>");
                    String formattedSource = sourceLines[l].replaceAll("<", "&lt;").replaceAll(">", "&gt;");
                    if(conditionals.length() > 0) {
                        pw.print("<a title=\"" + conditionalExpl.toString() +"\">");
                    }
                    pw.print(formattedSource);
                    if(conditionals.length() > 0) {
                        pw.println("</a>");
                    }
                    pw.println("</pre></span>");
                    pw.println("</td>");
                    pw.println("</tr>");
                }
                pw.println("</table>");
            }
        }
        pw.println("</div>");
        pw.println("</body></html>");
    }

    protected void printMenu(PrintWriter pw) {

    }
}
