package no.kantega.labs.revoc.demo;

import no.kantega.labs.revoc.registry.CoverageData;
import no.kantega.labs.revoc.registry.Registry;

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

        for(int lineNumber : lineNumbers) {
            long timesRun = data.getLinesVisited(classId)[lineNumber - 1];
            assertEquals("Expected line " + lineNumber + " to be run 1 times instead of " + timesRun, 1, timesRun);
            assertTrue("Expected line " + lineNumber + " last run time to be != 0", data.getLinesVisitTimes(classId)[lineNumber - 1] != 0);
        }
    }


    public void never(int... lineNumbers) {
        for(int lineNumber : lineNumbers) {
            assertEquals(0, data.getLinesVisited(classId)[lineNumber-1]);
            assertTrue("Expected line " + lineNumber + " last run time to be == 0", data.getLinesVisitTimes(classId)[lineNumber-1] == 0);
        }
    }
}
