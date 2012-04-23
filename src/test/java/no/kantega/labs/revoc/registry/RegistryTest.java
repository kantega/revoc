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

package no.kantega.labs.revoc.registry;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.BitSet;

import static org.junit.Assert.*;

/**
 */
public class RegistryTest {

    @Before
    public void setup() {
        Registry.resetRegistry();
    }

    @Test
    public void changeListenersShouldBeInvokedUnlessRemoved() throws InterruptedException {
        TestChangeListener listener = new TestChangeListener();

        Registry.addChangeListener(listener);

        Registry.linesTouched(0);

        Thread.sleep(Registry.NOTIFY_CHANGE_RESOLUTION_MILLIS* 2);

        assertTrue(listener.isInvoked());

        listener.setInvoked(false);

        Registry.removeChangeListener(listener);
        Registry.linesTouched(0);
        
        Thread.sleep(Registry.NOTIFY_CHANGE_RESOLUTION_MILLIS*2);

        assertFalse(listener.isInvoked());




    }

    @Test
    public void saveAndLoadShouldBringBackSameInfo() {

        // Given
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int classId = Registry.registerClass("class", getClass().getClassLoader(), "source");
        Registry.registerLines(classId, new int[] {1,2,3,4});
        BranchPoint[] branchPoints = new BranchPoint[] {new BranchPoint(0, 1)};
        Registry.registerBranchPoints(classId, Arrays.asList(branchPoints));
        Registry.save(out);
        Registry.registerClass("class1", getClass().getClassLoader(), "source1");


        // When
        Registry.load(new ByteArrayInputStream(out.toByteArray()));

        // Then
        assertTrue(Registry.isClassRegistered("class", getClass().getClassLoader()));
        assertFalse(Registry.isClassRegistered("class1", getClass().getClassLoader()));
        assertEquals(1, Registry.newClassId("someclass", getClass().getClassLoader()));
        assertEquals(1, Registry.getCoverageData().getClassNames().length);
        assertEquals("class", Registry.getCoverageData().getClassNames()[0]);
        assertEquals("source", Registry.getCoverageData().getSourceFiles()[0]);
        assertArrayEquals(new long[] {0,0,0,0}, Registry.getCoverageData().getLinesVisited(classId));
        assertEquals(1, Registry.getCoverageData().getBranchPoints(classId).length);
        



    }

    class TestChangeListener implements Registry.ChangeListener {
        private volatile boolean invoked;

        public void onChange(BitSet bs) {
            invoked = true;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void setInvoked(boolean invoked) {
            this.invoked = invoked;
        }
    }



}
