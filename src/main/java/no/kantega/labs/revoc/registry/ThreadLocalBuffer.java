package no.kantega.labs.revoc.registry;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 *
 */
public final class ThreadLocalBuffer implements Runnable {
    private static final int MULTI_METHOD_SLOTS = 512;
    private static final int MULTI_METHOD_BUFFER_SIZE = MULTI_METHOD_SLOTS*5;
    private final long[] multiMethodVisits = new long[MULTI_METHOD_BUFFER_SIZE];
    private final long[] lineVisits = new long[MULTI_METHOD_SLOTS*32];


    private static final int METHOD_SLOTS = 512;
    private static final int METHOD_BUFFER_SIZE = METHOD_SLOTS * 3;
    private final long[] methodVisits = new long[METHOD_BUFFER_SIZE];



    private long lastFlush;

    private int flushes;

    private long stacktop = -1;
    private int lineIndex;


    public ThreadLocalBuffer() {
        for (int i = 0; i < MULTI_METHOD_BUFFER_SIZE; i +=5) {
            multiMethodVisits[i] = -1;
        }
        for (int i = 0; i < METHOD_BUFFER_SIZE; i += 3) {
            methodVisits[i] = -1;
        }
    }



    public final void visitMethod(long methodId) {

            int index = getMethodIndex(methodId);

        long[] visits = methodVisits;
        long slotMethod = getMethodAtIndex(index, visits);


            visitSlotMethod(methodId, index, slotMethod, visits);

    }

    public final int visitMultiMethod(long methodId, long visitedLines, int lines) {

            int index = getMultiMethodIndex(methodId);

            long slotMethod = getMultimethodSlot(index);


            return multimethodAtSlot(methodId, slotMethod, lines, index);
    }

    public final void visitLine(int cursor, int numvisits, int lineIndex, long methodId) {
        if(cursor != -1)  {
            lineVisits[cursor + lineIndex] += numvisits;
        } else {
            flushLineVisits(numvisits, methodId, lineIndex);
        }
    }

    private void visitSlotMethod(long methodId, int index, long slotMethod, long[] visits) {
        if(slotMethod == methodId) {
            recordMethodVisitAtIndex(index, visits);
        } else  {
            maybeFirstMethodVisit(methodId, index, slotMethod);
        }
    }

    private void maybeFirstMethodVisit(long methodId, int index, long slotMethod) {
        if(slotMethod == -1) {
            recordFirstMethodVisit(slotMethod, methodId, index);
        } else {
            flushMethodVisits(methodId, 1, Registry.time);
        }
    }



    private int getMethodIndex(long methodId) {
        return (hashCode(methodId) % METHOD_SLOTS) * 3;
    }

    private int hashCode(long methodId) {
        return (((int) (methodId ^ (methodId >>> 32))) & 0x7fffffff);
    }

    private long getMethodAtIndex(int index, long[] visits) {
        return visits[index];
    }



    private void recordFirstMethodVisit(long slotMethod, long methodId, int index) {

        long[] visits = methodVisits;
        if(slotMethod == -1) {
            visits[index] = methodId;
            recordMethodVisitAtIndex(index);
        }

    }

    private void recordMethodVisitAtIndex(int index) {
        recordMethodVisitAtIndex(index, methodVisits);
    }

    private int recordExistingMultiMethod(int index) {
        long[] visits = multiMethodVisits;
        recordMethodVisitAtIndex(index, visits);
        return (int) visits[index+4];
    }

    private void recordMethodVisitAtIndex(int index, long[] visits) {
        visits[++index]  ++;
        visits[++index] = Registry.time;
    }

    private int  recordFirstMultiMethodVisit(long methodId, int index, int lines) {
        long[] visits = multiMethodVisits;
        visits[index] = methodId;
        recordMethodVisitAtIndex(index, visits);
        visits[index + 3] = lines;
        long lineIndex = allocateLineIndex(lines);
        visits[index + 4] = lineIndex;
        return (int) lineIndex;
    }

    public final void flushLineVisits() {

        long[] visits = multiMethodVisits;
        //flushes++;
        for (int i = 0; i < visits.length; i+=5 ) {

            long methodId = visits[i];
            if(methodId != -1) {
                long methodVisits = visits[i + 1];
                visits[i+1] = 0;
                long methodTime = visits[i + 2];
                long lines = visits[i + 3];
                int lineIndex = (int) visits[i + 4];
                flushMethodVisits(methodId, methodVisits, methodTime);
                for(int l  = 0; l < lines; l++) {
                    int lindex = l + lineIndex;
                    if(lindex >= lineVisits.length) {
                        return;
                    }
                    flushLineVisits(lineVisits[lindex], methodId, l);
                    lineVisits[lindex] = 0;
                }
            }

        }
    }

    private long allocateLineIndex(int lines) {
        int current = lineIndex;
        int next = lineIndex + lines;
        if(next >= lineVisits.length) {
            throw  new ArrayIndexOutOfBoundsException("whoops");
        }
        lineIndex = next;
        return current;
    }

    public final void flushMethodVisits() {

        for (int i = 0; i < METHOD_BUFFER_SIZE; i += 3) {
            long methodId = getMethodAtIndex(i, methodVisits);
            if (methodId != -1) {
                flushMethodVisits(methodId, methodVisits[i + 1], methodVisits[i + 2]);
                methodVisits[i+1] = 0;

            }
        }
    }

    private void flushMethodVisits(long methodId, long visits, long lastTime) {

        int cid = (int) (methodId >> 32);
        int mid = (int) (methodId & 0xFFFFFFFFl);
        Registry.methodVisits[cid].add(mid, visits);

        Registry.classTouches.lazySet(cid, 1);

        if (Registry.methodFirstLines[cid][mid] >= Registry.lineTimes[cid].length()) {
            return;
        }
        Registry.lineTimes[cid].set(Registry.methodFirstLines[cid][mid], lastTime);

    }

    private void flushLineVisits(long times, long methodId, int lineIndex) {
        if (times == 0) {
            return;
        }
        int cid = (int) (methodId >> 32);
        int mid = (int) (methodId & 0xFFFFFFFFl);
        AtomicLongArray classVisits = Registry.lineVisits[cid];

        int firstLine = Registry.methodFirstLines[cid][mid];
        if (lineIndex + firstLine >= classVisits.length()) {
            return;
        }
        classVisits.addAndGet(lineIndex + firstLine, times);

    }

    private void flushLineTime(long methodId, int lineIndex, long time) {
        int cid = (int) (methodId >> 32);
        int mid = (int) (methodId & 0xFFFFFFFFl);
        int firstLine = Registry.methodFirstLines[cid][mid];
        AtomicLongArray lineTime = Registry.lineTimes[cid];
        flushLineTime(lineIndex, firstLine, time, lineTime);


    }

    private void flushLineTime(int lineIndex, int firstLine, long time, AtomicLongArray lineTime) {
        int index = lineIndex + firstLine;
        if (index >= lineTime.length()) {
            return;
        }
        lineTime.set(index, time);
    }

    private int multimethodAtSlot(long methodId, long slotMethod, int lines, int index) {
        if (slotMethod == methodId) {
            return recordExistingMultiMethod(index);
        } else {
            return maybeFirstMultimethod(slotMethod, methodId, lines, index);
        }
    }

    private long getMultimethodSlot(int index) {
        return multiMethodVisits[index];
    }

    private int maybeFirstMultimethod(long slotMethod, long methodId, int lines, int index) {
        if(slotMethod == -1) {
            return recordFirstMultiMethodVisit(methodId, index, lines);
        } else {
            flushMethodVisits(methodId, 1, Registry.time);
            return -1;
        }
    }


    private int getMultiMethodIndex(long methodId) {
        return (hashCode(methodId) % MULTI_METHOD_SLOTS) * 5;
    }

    private void flushVisitedLinesTime(long methodId, long visitedLines, int lines, long lastTime, long lastVisitedLines) {
        int cid = (int) (methodId >> 32);
        int mid = (int) (methodId & 0xFFFFFFFFl);
        int firstLine = Registry.methodFirstLines[cid][mid];
        AtomicLongArray lineTime = Registry.lineTimes[cid];
        for (int i = 0; i < lines; i++) {
            long mask = 1 << i;
            if ((lastVisitedLines & mask) != 0 && (visitedLines & mask) == 0) {
                flushLineTime(i, firstLine, lastTime, lineTime);
            }
        }
    }

    public final void pushStack(long methodId) {
        if (isStackTop(-1)) {
            stacktop = methodId;
        }

    }

    public final void popStack(long methodId) {

        if (isStackTop(methodId)) {
            flushStackTop();
        } else if (Registry.time != lastFlush) {
            //lastFlush = time;
            //flushLineVisits();
            //flushMethodVisits();
        }
    }

    private void flushStackTop() {
        lastFlush = Registry.time;
        flushMethodVisits();
        flushLineVisits();
        stacktop = -1;

        //System.out.println("NUmFlush: " + flushes);
        flushes = 0;
    }

    private boolean isStackTop(long methodId) {
        return stacktop == methodId;
    }

    @Override
    public void run() {

    }
}
