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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class BranchPoint implements Serializable{
    private final int instruction;
    private final int linenumber;
    private AtomicLong before = new AtomicLong();
    private AtomicLong after = new AtomicLong();

    public BranchPoint(int instruction, int linenumber) {
        this.instruction = instruction;
        this.linenumber = linenumber;
    }

    public int getInstruction() {
        return instruction;
    }

    public int getLinenumber() {
        return linenumber;
    }

    public void before() {
        before.incrementAndGet();
    }


    public  void after() {
        after.incrementAndGet();
    }

    public long getAfter() {
        return after.longValue();
    }

    public long getBefore() {
        return before.longValue();
    }

    public boolean isAlwaysBranched() {
        return getBefore() != 0 && getAfter() == 0;
    }
    public boolean isNeverBranched() {
        return (getBefore()-getAfter()) == 0;
    }

    public  void reset() {
        before.set(0);
        after.set(0);
    }

    public  void after(int num) {
        after.addAndGet(num);
    }

    public  void before(int num) {
        before.addAndGet(num);
    }
}