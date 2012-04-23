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

import no.kantega.labs.revoc.report.HtmlReport;
import no.kantega.labs.revoc.source.CompondSourceSource;
import no.kantega.labs.revoc.source.MavenProjectSourceSource;
import no.kantega.labs.revoc.source.MavenSourceArtifactSourceSource;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 *
 */
public abstract class Registry {

    private static int classCount = 0;
    private static final int INITIAL_NUM_CLASSES = 1;
    private static String[] classNames;
    private static ConcurrentMap<Integer, ClassNameMap> classNamesMap;

    private static String[] sourceFiles;
    private static int[] classLoaders;
    public static int[][] lines;
    public static AtomicLongArray[] lineVisits;
    public static AtomicLongArray[] lineTimes;
    public static AtomicIntegerArray classTouches;
    private static BranchPoint[][] branchPoints;

    public static volatile long time = 0;
    public static final int CHECK_RESOLUTION_MILLIS = 50;
    public static final int NOTIFY_CHANGE_RESOLUTION_MILLIS = 500;
    public static final int TIME_RESOLUTION_MILLIS = 50;

    private final static List<ChangeListener> changeListeners = new ArrayList<ChangeListener>();

    private static final Object monitor = new Object();

    private static final ThreadMap threadMap = new ThreadMap();

    private static String[][] methodNames;
    private static String[][] methodDescs;

    public static Collection<Frame> getFrames() {
        Collection<FrameMap> frameMaps;
        synchronized (threadMap) {
            frameMaps = new HashSet<FrameMap>(threadMap.values());
        }


        Frame top = new Frame(-1);

        for (FrameMap map : frameMaps) {
            Collection<Frame> frames;
            synchronized (map) {
                frames = new HashSet<Frame>(map.values());
            }


            for (Frame frame : frames) {
                frame.mergeIntoParent(top);
            }

        }

        return top.getChildren();
    }

    public static ClassLoader getClassLoader(int classLoader) {
        return classNamesMap.get(classLoader).getClassLoader();
    }

    public interface ChangeListener {

        void onChange(BitSet bs);
    }

    static class ClassNameMap extends ConcurrentHashMap<String, Integer> {

        private final ClassLoader classLoader;

        public ClassNameMap(ClassLoader classLoader) {

            this.classLoader = classLoader;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }

    static {
        try {
            resetRegistry();
            startTimerThread();
            startChangeThread();

            URL resource = Registry.class.getClassLoader().getResource("revoc-instrumentation.properties");
            if (resource != null) {
                Properties props = new Properties();
                props.load(resource.openStream());

                String registry = props.getProperty("registry");
                if (registry != null) {
                    FileInputStream fis = new FileInputStream(registry);
                    load(fis);
                    fis.close();
                }

                String report = props.getProperty("report");
                if (report != null) {
                    addReportShutdownHook(report);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void startTimerThread() {
        new Thread("Revoc time ticker") {
            {
                setDaemon(true);
            }

            @Override
            public void run() {
                while (true) {

                    try {
                        Thread.sleep(TIME_RESOLUTION_MILLIS);

                        time = System.currentTimeMillis();

                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }.start();
    }

    public static void addChangeListener(ChangeListener changeListener) {
        synchronized (changeListeners) {
            changeListeners.add(changeListener);
        }
    }

    public static void removeChangeListener(ChangeListener changeListener) {
        synchronized (changeListeners) {
            changeListeners.remove(changeListener);
        }
    }

    private static void startChangeThread() {
        new Thread("Revoc change detector") {
            {
                setDaemon(true);
            }

            private long lastInvoke = 0;
            @Override
            public void run() {
                BitSet bs = new BitSet();

                while (true) {

                    AtomicIntegerArray classes = classTouches;

                    for(int i = 0; i < classes.length(); i++) {
                        int last = classes.get(i);
                        if(last != 0) {
                            bs.set(i);
                            classes.set(i, 0);
                        }
                    }
                    long now = System.currentTimeMillis();
                    long sincelast = now - lastInvoke;
                    if (bs.cardinality() > 0 && sincelast > NOTIFY_CHANGE_RESOLUTION_MILLIS ) {
                        List<ChangeListener> listeners = new ArrayList<ChangeListener>();
                        synchronized (changeListeners) {
                            listeners.addAll(changeListeners);
                        }
                        for (ChangeListener listener : listeners) {
                            try {
                                listener.onChange(bs);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        bs.clear();
                        lastInvoke  = now;
                    }

                    try {
                        Thread.sleep(CHECK_RESOLUTION_MILLIS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }.start();
    }

    private static void addReportShutdownHook(final String report) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    File reportFile = new File(report);
                    reportFile.getParentFile().mkdirs();
                    final FileWriter writer = new FileWriter(reportFile);
                    new HtmlReport().run(getCoverageData(), null, writer, new CompondSourceSource(new MavenProjectSourceSource(), new MavenSourceArtifactSourceSource()));
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public static void load(InputStream inputStream) {
        try {
            ObjectInputStream in = new ObjectInputStream(inputStream);
            Save s = (Save) in.readObject();
            classCount = s.classCount;
            classNames = s.classNames;
            classLoaders = s.classLoaders;
            methodNames = s.methodNames;
            methodDescs = s.methodDescs;
            classNamesMap = new ConcurrentHashMap<Integer, ClassNameMap>();
            for (int i = 0; i < classCount; i++) {
                if(!classNamesMap.containsKey(classLoaders[i])) {
                    classNamesMap.putIfAbsent(classLoaders[i], new ClassNameMap(null));
                }
                classNamesMap.get(classLoaders[i]).put(classNames[i], i);
            }
            sourceFiles = s.sourceFiles;
            lines = s.lines;
            lineVisits = s.lineVisits;
            branchPoints = s.branchPoints;
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void save(OutputStream outputStream) {
        try {

            ObjectOutputStream out = new ObjectOutputStream(outputStream);
            Save s = new Save(classCount, classNames, classLoaders, methodNames, methodDescs, sourceFiles, lines, lineVisits, branchPoints);
            out.writeObject(s);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void registerLineVisited(int classId, int lineId) {
        lineVisits[classId].incrementAndGet(lineId);
    }

    public static void registerLineTimeVisited(AtomicLongArray lineVisits, AtomicLongArray lineTimes, int lineId, int numvisits, long time) {
        lineVisits.addAndGet(lineId, numvisits);
        if(numvisits != 0) {
            lineTimes.set(lineId, time);
        }
    }

    public static void registerLineVisited(AtomicLongArray lineVisits, int lineId, int numvisits) {
        lineVisits.addAndGet(lineId, numvisits);
    }


    public static void registerLineVisitedArray(AtomicLongArray lineVisits, int[] methodVisited, int firstLine) {
        for (int i = 0; i < methodVisited.length; i++) {
            lineVisits.addAndGet(i + firstLine, methodVisited[i]);
        }
    }

    public static void registerLineTimeVisitedArray(AtomicLongArray lineVisits, AtomicLongArray timeVisits, int[] methodVisited, long[] methodTimes, int firstLine) {
        for (int i = 0; i < methodVisited.length; i++) {
            int visits = methodVisited[i];
            final int classLine = i + firstLine;
            lineVisits.addAndGet(classLine, visits);
            if (visits > 0) {
                timeVisits.set(classLine, methodTimes[i]);
            }
        }
    }

    public static void linesTouched(int classId) {
        classTouches.set(classId, 1);
    }

    public static boolean isClassRegistered(String name, ClassLoader classLoader) {
        synchronized (monitor) {
            ClassNameMap classNameMap = classNamesMap.get(System.identityHashCode(classLoader));
            return classNameMap != null && classNameMap.containsKey(name);
        }
    }

    public static int registerClass(String name, ClassLoader classLoader, String source) {
        synchronized (monitor) {
            ensureLineRegistryCapacity();
            int classId = classCount;
            classNames[classId] = name;
            sourceFiles[classId] = source;
            classLoaders[classId] = System.identityHashCode(classLoader);
            classCount++;
            if(!classNamesMap.containsKey(classLoaders[classId])) {
                classNamesMap.putIfAbsent(classLoaders[classId], new ClassNameMap(classLoader));
            }
            classNamesMap.get(classLoaders[classId]).put(classNames[classId], classId);
            return classId;
        }
    }

    public static void registerLines(int classId, int[] lines) {
        Registry.lineVisits[classId] = new AtomicLongArray(lines.length);
        Registry.lineTimes[classId] = new AtomicLongArray(lines.length);
        Registry.lines[classId] = new int[lines.length];
        System.arraycopy(lines, 0, Registry.lines[classId], 0, lines.length);
    }

    private static void ensureLineRegistryCapacity() {
        if (classCount + 1 >= classNames.length) {
            {
                String[] old = classNames;
                String[] classNames = new String[old.length * 2];
                System.arraycopy(old, 0, classNames, 0, old.length);
                Registry.classNames = classNames;
            }
            {
                int[] old = classLoaders;
                int[] classLoaders = new int[old.length * 2];
                System.arraycopy(old, 0, classLoaders, 0, old.length);
                Registry.classLoaders = classLoaders;
            }

            {
                String[][] old = methodNames;
                String[][] methodNames = new String[old.length * 2][];
                System.arraycopy(old, 0, methodNames, 0, old.length);
                Registry.methodNames = methodNames;
            }

            {
                String[][] old = methodDescs;
                String[][] methodDescs = new String[old.length * 2][];
                System.arraycopy(old, 0, methodDescs, 0, old.length);
                Registry.methodDescs = methodDescs;
            }
            {
                String[] old = sourceFiles;
                String[] sourceFiles = new String[old.length * 2];
                System.arraycopy(old, 0, sourceFiles, 0, old.length);
                Registry.sourceFiles = sourceFiles;
            }
            {
                AtomicLongArray[] old = lineVisits;
                AtomicLongArray[] lineVisits = new AtomicLongArray[old.length * 2];
                System.arraycopy(old, 0, lineVisits, 0, old.length);
                Registry.lineVisits = lineVisits;
            }
            {
                AtomicLongArray[] old = lineTimes;
                AtomicLongArray[] lineTimes = new AtomicLongArray[old.length * 2];
                System.arraycopy(old, 0, lineTimes, 0, old.length);
                Registry.lineTimes = lineTimes;
            }
            {
                int[][] old = lines;
                int[][] lines = new int[old.length * 2][];
                System.arraycopy(old, 0, lines, 0, old.length);
                Registry.lines = lines;
            }
            {
                BranchPoint[][] old = branchPoints;
                BranchPoint[][] branchPoints = new BranchPoint[old.length * 2][];
                System.arraycopy(old, 0, branchPoints, 0, old.length);
                Registry.branchPoints = branchPoints;
            }

            classTouches = new AtomicIntegerArray(classNames.length);
        }
    }

    public static CoverageData getCoverageData() {
        synchronized (monitor) {
            final String[] classNames = new String[classCount];
            System.arraycopy(Registry.classNames, 0, classNames, 0, classNames.length);

            final int[] classLoaders = new int[classCount];
            System.arraycopy(Registry.classLoaders, 0, classLoaders, 0, classLoaders.length);

            final String[][] methodNames = new String[classCount][];
            System.arraycopy(Registry.methodNames, 0, methodNames, 0, methodNames.length);

            final String[][] methodDescs = new String[classCount][];
            System.arraycopy(Registry.methodDescs, 0, methodDescs, 0, methodDescs.length);

            final String[] sourceFiles = new String[classCount];
            System.arraycopy(Registry.sourceFiles, 0, sourceFiles, 0, sourceFiles.length);

            final long[][] lineVisits = new long[classCount][];
            final long[][] lineTimes = new long[classCount][];
            for (int c = 0; c < classCount; c++) {
                AtomicLongArray registryVisits = Registry.lineVisits[c];
                int[] lines = Registry.lines[c];

                int maxLine = 0;

                for (int l = 0; l < lines.length; l++) {
                    maxLine = Math.max(maxLine, lines[l]);
                }
                lineVisits[c] = new long[maxLine];
                Arrays.fill(lineVisits[c], -1);
                lineTimes[c] = new long[maxLine];
                Arrays.fill(lineTimes[c], -1);

                synchronized (Registry.lineVisits[c]) {
                    for (int l = 0; l < registryVisits.length(); l++) {
                        int lineNumber = lines[l];
                        lineVisits[c][lineNumber - 1] = registryVisits.get(l);
                    }
                    final AtomicLongArray registryTimes = Registry.lineTimes[c];
                    for (int l = 0; l < registryTimes.length(); l++) {
                        int lineNumber = lines[l];
                        lineTimes[c][lineNumber - 1] = registryTimes.get(l);
                    }
                }

            }

            final BranchPoint[][] branchPoints = new BranchPoint[classCount][];

            System.arraycopy(Registry.branchPoints, 0, branchPoints, 0, branchPoints.length);

            return new CoverageData() {
                public long[] getLinesVisited(int classId) {
                    return lineVisits[classId];
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
                public ClassLoader getClassLoader(int i) {
                    return Registry.getClassLoader(i);
                }

                public String[] getSourceFiles() {
                    return sourceFiles;
                }

                public String[][] getMethodNames() {
                    return methodNames;
                }

                public String[][] getMethodDescriptions() {
                    return methodDescs;
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
            };
        }
    }

    public static void registerMethods(int classId, List<String> methodNames, List<String> methodDescs) {
        Registry.methodNames[classId] = new String[methodNames.size()];
        Registry.methodDescs[classId] = new String[methodNames.size()];
        for (int i = 0; i < methodNames.size(); i++) {
            Registry.methodNames[classId][i] = methodNames.get(i);
            Registry.methodDescs[classId][i] = methodDescs.get(i);
        }
    }

    public static void registerBranchPoints(int classId, List<BranchPoint> branchPoints) {
        Registry.branchPoints[classId] = branchPoints.toArray(new BranchPoint[branchPoints.size()]);
    }

    public static void registerBranchPointVisits(int classId, int index, int numBefore, int numAfter) {
        final BranchPoint branchPoint = branchPoints[classId][index];
        branchPoint.before(numBefore);
        branchPoint.after(numAfter);
    }

    public static void registerBranchPointVisitsArray(int classId, int[] beforeVisits, int[] afterVisits, int firstIndex) {

        for (int i = 0; i < beforeVisits.length; i++) {
            final BranchPoint branchPoint = branchPoints[classId][firstIndex + i];
            branchPoint.before(beforeVisits[i]);
            branchPoint.after(afterVisits[i]);
        }
    }

    public static int newClassId(String className, ClassLoader classLoader) {
        synchronized (monitor) {
            ClassNameMap classNameMap = classNamesMap.get(System.identityHashCode(classLoader));

            if (classNameMap != null && classNameMap.containsKey(className)) {
                return classNameMap.get(className);
            } else {
                return classCount;
            }
        }
    }

    public static void resetRegistry() {
        synchronized (monitor) {
            classCount = 0;
            classNames = new String[INITIAL_NUM_CLASSES];
            classLoaders = new int[INITIAL_NUM_CLASSES];
            classTouches = new AtomicIntegerArray(INITIAL_NUM_CLASSES);
            classNamesMap = new ConcurrentHashMap<Integer, ClassNameMap>(INITIAL_NUM_CLASSES);
            methodNames = new String[INITIAL_NUM_CLASSES][];
            methodDescs = new String[INITIAL_NUM_CLASSES][];
            sourceFiles = new String[INITIAL_NUM_CLASSES];
            lines = new int[INITIAL_NUM_CLASSES][];
            lineVisits = new AtomicLongArray[INITIAL_NUM_CLASSES];
            lineTimes = new AtomicLongArray[INITIAL_NUM_CLASSES];
            branchPoints = new BranchPoint[INITIAL_NUM_CLASSES][];

        }

    }

    public static void resetVisits() {
        synchronized (monitor) {
            for (int i = 0; i < classCount; i++) {
                AtomicLongArray lvs = lineVisits[i];
                for (int l = 0; l < lvs.length(); l++) {
                    if (lvs.get(l) >= 0) {
                        lvs.set(l,  0);
                    }
                }
                BranchPoint[] bps = branchPoints[i];
                for (int b = 0; bps != null && b < bps.length; b++) {
                    bps[b].reset();

                }
            }
        }
    }

    public static FrameMap registerMethodEnter(long methodId) {

        FrameMap frameMap = getFrameMapForThread();

        Frame myFrame = frameMap.getFrameForMethod(methodId);

        frameMap.setTopFrame(myFrame);

        myFrame.setStartTime(System.nanoTime());

        return frameMap;
    }

    private static FrameMap getFrameMapForThread() {
        long threadId = Thread.currentThread().getId();
        FrameMap frameMap = threadMap.get(threadId);

        if (frameMap == null) {
            threadMap.putIfAbsent(threadId, frameMap = new FrameMap());
        }
        return frameMap;
    }


    public static void registerMethodExit(FrameMap frameMap, long exitTime, long waittime) {
        Frame topFrame = frameMap.getTopFrame();
        Frame parentFrame = topFrame.getParentFrame();
        frameMap.setTopFrame(parentFrame);
        topFrame.visit(exitTime - topFrame.getStartTime(), waittime);
    }

    private static class Save implements Serializable {

        final int classCount;
        final String[] classNames;
        final int[] classLoaders;
        final String[][] methodNames;
        final String[][] methodDescs;
        final String[] sourceFiles;
        final int[][] lines;
        final AtomicLongArray[] lineVisits;
        final BranchPoint[][] branchPoints;

        public Save(int classCount, String[] classNames, int[] classLoaders, String[][] methodNames, String[][] methodDescs, String[] sourceFiles, int[][] lines, AtomicLongArray[] lineVisits, BranchPoint[][] branchPoints) {

            this.classCount = classCount;
            this.classNames = classNames;
            this.classLoaders = classLoaders;
            this.methodNames = methodNames;
            this.methodDescs = methodDescs;
            this.sourceFiles = sourceFiles;
            this.lines = lines;
            this.lineVisits = lineVisits;
            this.branchPoints = branchPoints;
        }
    }

    public static void dumpFrames() {
        Collection<Frame> frames = getFrames();
        synchronized (frames) {
            for (Frame frame : frames) {
                dumpFrame(frame, 0);
            }
        }
    }

    private static void dumpFrame(Frame frame, int level) {
        for (int i = 0; i < level; i++) {
            System.out.print("  ");
        }

        int classId = frame.getClassId();
        int methodIdx = frame.getMethodIndex();

        String className = classNames[classId];
        String methodName = methodNames[classId][methodIdx];

        FrameData data = frame.getData();
        long selfTime = data.getTime();
        for (Frame child : frame.getChildren()) {
            selfTime -= child.getData().getTime();
        }
        System.out.println(className + "." + methodName
                + " Total time: " + (TimeUnit.NANOSECONDS.toMillis(data.getTime()))
                + ", visits: " + data.getVisits()
                + ", self time: " + TimeUnit.NANOSECONDS.toMillis(selfTime)
                + ", wait time: " + TimeUnit.NANOSECONDS.toMillis(data.getWaittime()));
        for (Frame child : frame.getChildren()) {
            dumpFrame(child, ++level);
        }
    }

    public static class Frame {
        private final long methodId;
        private final Frame parentFrame;

        private Map<Long, Frame> children = new ConcurrentHashMap<Long, Frame>();
        private long time;
        private int visits;
        private volatile long startTime;

        private Frame(long methodId, Frame parentFrame) {
            this.methodId = methodId;
            this.parentFrame = parentFrame;
        }

        private Frame(long methodId) {
            this(methodId, null);
        }

        public void addChild(Frame frame) {
            children.put(frame.getMethodId(), frame);
        }


        public Frame getChild(long methodId) {
            return children.get(methodId);
        }

        public Frame getParentFrame() {
            return parentFrame;
        }

        public long getMethodId() {
            return methodId;
        }

        public int getClassId() {
            return (int) (methodId >> 32);
        }

        public int getMethodIndex() {
            return (int) (methodId & 0xFFFFFFFFl);
        }

        public Collection<Frame> getChildren() {
            return new ArrayList<Frame>(children.values());
        }

        public synchronized void visit(long time, long waittime) {
            this.time += time;
            this.visits++;
            this.startTime = -1;
        }

        public synchronized void visits(long visits, long time, long waittime) {
            this.time += time;
            this.visits += visits;
        }

        public synchronized FrameData getData() {
            return new FrameData(time, 0, visits, startTime);
        }


        public void mergeIntoParent(Frame parent) {

            Frame myFrame = parent.getChild(getMethodId());
            if (myFrame == null) {
                parent.addChild(myFrame = new Frame(getMethodId()));
            }
            FrameData data = getData();
            long time = data.getTime();
            int visits = data.getVisits();
            // If method is currently running
            if (data.getStartTime() != -1) {
                time += (System.nanoTime() - data.getStartTime());
                visits++;
            }

            myFrame.visits(visits, time, data.getWaittime());

            for (Frame child : getChildren()) {
                child.mergeIntoParent(myFrame);
            }
        }

        public synchronized void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public synchronized long getStartTime() {
            return startTime;
        }
    }

    public static class FrameData {

        private final long time;
        private final long waittime;
        private final int visits;
        private final long startTime;

        public FrameData(long time, long waittime, int visits, long startTime) {
            this.time = time;
            this.waittime = waittime;
            this.visits = visits;
            this.startTime = startTime;
        }

        public long getTime() {
            return time;
        }

        public int getVisits() {
            return visits;
        }

        public long getWaittime() {
            return waittime;
        }

        public long getStartTime() {
            return startTime;
        }
    }

    static class ThreadMap extends ConcurrentHashMap<Long, FrameMap> {

    }

    public static class FrameMap extends ConcurrentHashMap<Long, Frame> {
        private Frame topFrame;

        public Frame getTopFrame() {
            return topFrame;
        }

        public void setTopFrame(Frame topFrame) {
            this.topFrame = topFrame;
        }

        public Frame getFrameForMethod(long methodId) {
            Frame parentFrame = getTopFrame();

            Frame myFrame;
            if (parentFrame != null) {
                myFrame = parentFrame.getChild(methodId);
                if (myFrame == null) {
                    myFrame = new Frame(methodId, parentFrame);
                    parentFrame.addChild(myFrame);
                }
            } else {
                myFrame = get(methodId);
                if (myFrame == null) {
                    myFrame = new Frame(methodId);
                    put(methodId, myFrame);
                }
            }
            return myFrame;
        }
    }
}
