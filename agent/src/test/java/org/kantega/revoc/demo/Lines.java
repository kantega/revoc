package org.kantega.revoc.demo;

import org.kantega.revoc.registry.CoverageData;
import org.kantega.revoc.registry.Registry;

import java.util.BitSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class Lines {

    private final int classId;
    private BitSet run = new BitSet();
    private BitSet notRun = new BitSet();

    private final CoverageData data;

    public Lines(int classId) {
        this.classId = classId;
        data = Registry.getCoverageData();
    }

    public void once(int... lineNumbers) {
        times(1, lineNumbers);
    }

    public void twice(int... lineNumbers) {
        times(2, lineNumbers);
    }

    public void times(int times, int... lineNumbers) {
        for(int lineNumber : lineNumbers) {
            long timesRun = data.getLinesVisited(classId)[lineNumber - 1];
            assertEquals("Expected line " + lineNumber + " to be run " + times + " times instead of " + timesRun, times, timesRun);
            if (times > 0) {
                assertTrue("Expected line " + lineNumber + " last run time to be != 0", data.getLinesVisitTimes(classId)[lineNumber - 1] != 0);
            } else {
                assertTrue("Expected line " + lineNumber + " last run time to be == 0", data.getLinesVisitTimes(classId)[lineNumber - 1] == 0);

            }
        }

    }


    public void never(int... lineNumbers) {

        times(0, lineNumbers);

    }
}
