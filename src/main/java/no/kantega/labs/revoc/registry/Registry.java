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

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.procedure.TLongLongProcedure;
import no.kantega.labs.revoc.report.HtmlReport;
import no.kantega.labs.revoc.source.CompondSourceSource;
import no.kantega.labs.revoc.source.MavenProjectSourceSource;
import no.kantega.labs.revoc.source.MavenSourceArtifactSourceSource;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;
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
    private static ThreadLocal<ThreadLocalBuffer> threadLocalBuffer = new ThreadLocal<ThreadLocalBuffer>() {
        @Override
        protected ThreadLocalBuffer initialValue() {
            return new ThreadLocalBuffer();
        }
    };
    private static final int INITIAL_NUM_CLASSES = 1;
    private static String[] classNames;
    private static ConcurrentMap<Integer, ClassNameMap> classNamesMap;

    private static String[] sourceFiles;
    private static int[] classLoaders;
    public static int[][] lines;
    public static AtomicLongArray[] lineVisits;
    public static AtomicLongArray[] methodVisits;
    public static int[][][] methodLines;
    public static AtomicLongArray[] lineTimes;
    public static AtomicIntegerArray classTouches;
    private static BranchPoint[][] branchPoints;

    public static volatile long time = 0;
    public static final int CHECK_RESOLUTION_MILLIS = 100;
    public static final int NOTIFY_CHANGE_RESOLUTION_MILLIS = 1000;
    public static final int TIME_RESOLUTION_MILLIS = 50;

    private final static List<ChangeListener> changeListeners = new ArrayList<ChangeListener>();

    private static final Object monitor = new Object();

    private static final ThreadMap threadMap = new ThreadMap();

    private static final ThreadLocal<FrameMap> threadLocalMap = new ThreadLocal<FrameMap>() {
        @Override
        protected FrameMap initialValue() {
            FrameMap frameMap = new FrameMap();
            threadMap.put(Thread.currentThread().getId(), frameMap);
            return frameMap;
        }
    };

    private static String[][] methodNames;
    private static String[][] methodDescs;
    private static List<NamedClassLoader> classLoaderList = new ArrayList<NamedClassLoader>();
    private static int[][] methodFirstLines;

    private static Unsafe unsafe;

    private static long offset;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
            offset = unsafe.objectFieldOffset(Thread.class.getDeclaredField("target"));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    public static Collection<Frame> getFrames() {


        Frame top = new Frame(-1);

        for (FrameMap map : threadMap.values()) {
            for (Frame frame : map.values()) {
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

    public static class NamedClassLoader {
        private String name;
        private ClassLoader classLoader;

        NamedClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            this.name = classLoader.toString();
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public String getName() {
            return name;
        }
    }

    public static void addClassLoader(ClassLoader classLoader) {
        synchronized (monitor) {
            classLoaderList.add(new NamedClassLoader(classLoader));
            Collections.sort(classLoaderList, new Comparator<NamedClassLoader>() {
                @Override
                public int compare(NamedClassLoader classLoader, NamedClassLoader classLoader1) {
                    return classLoader.getName().toLowerCase().compareTo(classLoader1.getName().toLowerCase());
                }
            });
        }
    }
    public static List<NamedClassLoader> getClassLoaders() {

        synchronized (monitor) {
            return new ArrayList<NamedClassLoader>(classLoaderList);
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



    public static ThreadLocalBuffer getThreadLocalBufferInc(long methodId) {
        ThreadLocalBuffer buffer = getThreadLocalBuffer();
        buffer.pushStack(methodId);
        return buffer;
    }
    public static ThreadLocalBuffer getThreadLocalBuffer() {

        Thread thread = Thread.currentThread();
        Object counter = unsafe.getObject(thread, offset);

        if(counter == null) {
            return newBuffer();
        }
        try {
            return (ThreadLocalBuffer) counter;
        } catch (Exception e) {
            return newBuffer();
        }

    }

    private static ThreadLocalBuffer newBuffer() {
        ThreadLocalBuffer buffer = new ThreadLocalBuffer();
        unsafe.putObject(Thread.currentThread(), offset, buffer);
        return buffer;
    }


    public static void registerLineTimeVisited(AtomicLongArray lineVisits, AtomicLongArray lineTimes, int lineId, int numvisits, long time) {
        if(numvisits != 0) {
            lineVisits.addAndGet(lineId, numvisits);
            lineTimes.lazySet(lineId, time);
        }

    }

    public static void registerLineTimeVisitedMV(AtomicLongArray lineVisits, AtomicLongArray lineTimes, int lineId, int numvisits, long time) {
        if(numvisits != 0) {
            lineVisits.addAndGet(lineId, numvisits);
        }
        if(numvisits != -1) {
            lineTimes.set(lineId, time);
        }
    }

    public static void registerOneLineVisited(long methodId) {
        //getThreadLocalBuffer().visitLine(methodId, 1, 1);
        getThreadLocalBuffer().visitMethod(methodId, Registry.time);
    }

    public static void registerLineVisitedMV(AtomicLongArray lineVisits, int lineId, int numvisits) {
        if(numvisits != 0) {
            lineVisits.addAndGet(lineId, numvisits);
        }
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
                addClassLoader(classLoader);
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
                AtomicLongArray[] old = methodVisits;
                AtomicLongArray[] methodVisits = new AtomicLongArray[old.length * 2];
                System.arraycopy(old, 0, methodVisits, 0, old.length);
                Registry.methodVisits = methodVisits;
            }

            {
                int[][] old = methodFirstLines;
                int[][] methodFirstLines = new int[old.length * 2][];
                System.arraycopy(old, 0, methodFirstLines, 0, old.length);
                Registry.methodFirstLines = methodFirstLines;
            }

            {
                int[][][] old = methodLines;
                int[][][] methodLines = new int[old.length * 2][][];
                System.arraycopy(old, 0, methodLines, 0, old.length);
                Registry.methodLines = methodLines;
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
                    int[][] methodLines = Registry.methodLines[c];
                    if(methodLines == null) {
                        continue;
                    }  else {
                        for(int methodIndex = 0; methodIndex < methodLines.length; methodIndex++) {
                            long methodTimes = methodVisits[c].get(methodIndex);
                            for(int l = 0; l < methodLines[methodIndex].length; l++) {
                                int idx = methodLines[methodIndex][l];
                                if(idx >= lines.length) {
                                    break;
                                }
                                int lineNumber = lines[idx];
                                lineVisits[c][lineNumber-1] += methodTimes;
                            }

                        }
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

    public static void registerMethods(int classId, List<String> methodNames, List<String> methodDescs, Map<Integer, List<Integer>> methodLineNumbers) {
        methodVisits[classId]  = new AtomicLongArray(methodNames.size());
        Registry.methodNames[classId] = new String[methodNames.size()];
        Registry.methodDescs[classId] = new String[methodNames.size()];
        for (int i = 0; i < methodNames.size(); i++) {
            Registry.methodNames[classId][i] = methodNames.get(i);
            Registry.methodDescs[classId][i] = methodDescs.get(i);
        }

        int[] firstLines = new int[methodLineNumbers.size()];
        methodFirstLines[classId] = firstLines;
        for(Integer methodIndex : methodLineNumbers.keySet()) {
            firstLines[methodIndex] = methodLineNumbers.get(methodIndex).get(0);
        }


        methodLines[classId] = new int[methodLineNumbers.size()][];
        for (Integer methodindex : methodLineNumbers.keySet()) {
            List<Integer> lines = methodLineNumbers.get(methodindex);
            methodLines[classId][methodindex] = new int[lines.size()];
            for(int i = 0; i < lines.size(); i++)  {
                methodLines[classId][methodindex][i] = lines.get(i);
            }
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
            methodLines = new int[INITIAL_NUM_CLASSES][][];
            methodVisits = new AtomicLongArray[INITIAL_NUM_CLASSES];
            methodFirstLines = new int[INITIAL_NUM_CLASSES][];
            lineTimes = new AtomicLongArray[INITIAL_NUM_CLASSES];
            branchPoints = new BranchPoint[INITIAL_NUM_CLASSES][];

        }

    }

    public static void resetVisits() {
        synchronized (monitor) {
            for (int i = 0; i < classCount; i++) {
                {
                    AtomicLongArray lvs = lineVisits[i];
                    for (int l = 0; l < lvs.length(); l++) {
                        if (lvs.get(l) >= 0) {
                            lvs.set(l,  0);
                        }
                    }
                }
                {
                    AtomicLongArray lvs = methodVisits[i];
                    for (int l = 0; l < lvs.length(); l++) {
                        if (lvs.get(l) >= 0) {
                            lvs.set(l,  0);
                        }
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

        FrameMap frameMap = threadLocalMap.get();

        Frame myFrame = frameMap.getFrameForMethod(methodId);

        frameMap.setTopFrame(myFrame);

        return frameMap;
    }

    private static FrameMap getFrameMapForThread() {
        return threadLocalMap.get();
    }


    public static void registerMethodExit(FrameMap frameMap, long exitTime, long startTime) {
        Frame topFrame = frameMap.getTopFrame();
        Frame parentFrame = topFrame.getParentFrame();
        frameMap.setTopFrame(parentFrame);
        topFrame.visit(exitTime - startTime);
    }

    public static void registerMethodExit(FrameMap frameMap) {
        Frame topFrame = frameMap.getTopFrame();
        Frame parentFrame = topFrame.getParentFrame();
        frameMap.setTopFrame(parentFrame);
        topFrame.visit();
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

        int classId = getClassId(frame.methodId);
        int methodIdx = getMethodIndex(frame.methodId);

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
                + ", self time: " + TimeUnit.NANOSECONDS.toMillis(selfTime));
        for (Frame child : frame.getChildren()) {
            dumpFrame(child, ++level);
        }
    }

    public static int getClassId(long methodId) {
        return (int) (methodId >> 32);
    }

    public static int getMethodIndex(long methodId) {
        return (int) (methodId & 0xFFFFFFFFl);
    }

    public static class Frame extends ConcurrentHashMap<Long, Frame> {
        private final long methodId;
        private final Frame parentFrame;
        private long time;
        private int visits;

        private Frame(long methodId, Frame parentFrame) {
            this.methodId = methodId;
            this.parentFrame = parentFrame;
        }

        private Frame(long methodId) {
            this(methodId, null);
        }

        public void addChild(Frame frame) {
            put(frame.getMethodId(), frame);
        }


        public Frame getChild(long methodId) {
            return get(methodId);
        }

        public Frame getParentFrame() {
            return parentFrame;
        }

        public long getMethodId() {
            return methodId;
        }



        public Collection<Frame> getChildren() {
            return new ArrayList<Frame>(values());
        }

        public synchronized void visit() {
            this.visits++;
        }

        public synchronized void visit(long time) {
            this.time += time;
            this.visits++;
        }

        public synchronized void visits(long visits, long time) {
            this.time += time;
            this.visits += visits;
        }


        public synchronized FrameData getData() {
            return new FrameData(time, visits);
        }

        public void mergeIntoParent(Frame parent) {

            Frame myFrame = parent.getChild(getMethodId());
            if (myFrame == null) {
                parent.addChild(myFrame = new Frame(getMethodId()));
            }
            FrameData data = getData();
            long time = data.getTime();
            int visits = data.getVisits();

            myFrame.visits(visits, time);

            for (Frame child : getChildren()) {
                child.mergeIntoParent(myFrame);
            }
        }

    }

    public static class FrameData {

        private final long time;
        private final int visits;

        public FrameData(long time, int visits) {
            this.time = time;
            this.visits = visits;
        }

        public long getTime() {
            return time;
        }

        public int getVisits() {
            return visits;
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
                myFrame = parentFrame.get(methodId);
                if (myFrame == null) {
                    myFrame = new Frame(methodId, parentFrame);
                    Frame put = parentFrame.putIfAbsent(methodId, myFrame);
                    if(put != null) {
                        return put;
                    }
                    return myFrame;
                } else {
                    return myFrame;
                }
            } else {
                myFrame = get(methodId);
                if (myFrame == null) {
                    myFrame = new Frame(methodId);
                    Frame put = putIfAbsent(methodId, myFrame);
                    if(put != null) {
                        return put;
                    } else {
                        return myFrame;
                    }
                } else {
                    return myFrame;
                }
            }
        }
    }

    public static final class ThreadLocalBuffer implements Runnable {
        private static final int LENGTH_PER_METHOD = 32 * 2 + 1;
        private static final int BUFFER_LENGTH = 8000;
        private static final int METHOD_BUFFER_SIZE = 4096*3*2;
        private long stacktop = -1;
        private final long[] lineVisits = new long[BUFFER_LENGTH];
        private int lineIndex;
        private int lineCursor;
        private final long[] methodVisits = new long[METHOD_BUFFER_SIZE];
        private int methodIndex;

        private long lastFlush;

        private long method1 = -1;
        private long times1 = 0;
        private long time1 = 0;

        private long method2 = -1;
        private long times2 = 0;
        private long time2 = 0;

        private long method3 = -1;
        private long times3 = 0;
        private long time3 = 0;

        private long method4 = -1;
        private long times4 = 0;
        private long time4 = 0;


        private long method5 = -1;
        private long times5 = 0;
        private long time5 = 0;


        private long method6 = -1;
        private long times6 = 0;
        private long time6 = 0;

        private TLongLongMap methodCountMap = new TLongLongHashMap(2000, 0.5f, 0, 0);
        private TLongLongMap methodTimeMap = new TLongLongHashMap(2000, 0.5f, 0, 0);

        private FlushMethodsAction flushMethodsAction = new FlushMethodsAction(methodCountMap);


        private int flushes;

        private TLongIntMap methodIndexMap = new TLongIntHashMap(1000, 0.5f, -1, -1);

        public ThreadLocalBuffer() {
            for(int i = 0; i < BUFFER_LENGTH; i+= LENGTH_PER_METHOD) {
                lineVisits[i] = -1;
            }
        }

        public final void visitMethod(long methodId, long time) {

            // We cache the index of the three most used methods after each flush
            if(method1 == methodId) {
                time1 = time;
                times1 ++;
            } else if(method2 == methodId) {
                time2 = time;
                times2 ++;
            } else if(method3 == methodId) {
                time3 = time;
                times3 ++;
            } else if(method4 == methodId) {
                time4 = time;
                times4 ++;
            } else if(method5 == methodId) {
                time5 = time;
                times5 ++;
            } else if(method6 == methodId) {
                time6 = time;
                times6 ++;
            }else {
                appendMethod(methodId, time);
            }

        }

        private void appendMethod(long methodId, long time) {
            if(methodIndex == METHOD_BUFFER_SIZE) {
                flushMethodVisits();
            }
            methodVisits[methodIndex++] = methodId;
            methodVisits[methodIndex++] = time;
        }


        class FlushMethodsAction implements TLongLongProcedure {
             long max, max2, max3, max4, max5, max6;
             long maxMethod, maxMethod2, maxMethod3, maxMethod4, maxMethod5, maxMethod6;
             
             private final TLongLongMap methodIndexMap;

             FlushMethodsAction(TLongLongMap methodIndexMap) {
                 this.methodIndexMap = methodIndexMap;
             }

             @Override
             public boolean execute(long methodId, long visits) {
                 if(visits > max) {
                     max = visits;
                     maxMethod = methodId;
                 } else if(visits > max2) {
                     max2 = visits;
                     maxMethod2 = methodId;
                 } else if(visits > max3) {
                     max3 = visits;
                     maxMethod3 = methodId;
                 } else if(visits > max4) {
                     max4 = visits;
                     maxMethod4 = methodId;
                 } else if(visits > max5) {
                     max5 = visits;
                     maxMethod5 = methodId;
                 }
                 else if(visits > max6) {
                     max6 = visits;
                     maxMethod6 = methodId;
                 }

                 flushMethodVisits(methodId, methodTimeMap.get(methodId), visits);
                 methodIndexMap.put(methodId, 0);

                 return true;
             }

             public void reset() {
                 max = 0;
                 maxMethod = -1;
                 max2 = 0;
                 maxMethod2 = -1;
                 max3 = 0;
                 maxMethod3 = -1;
             }
         }
        public final void flushMethodVisits() {

            //flushes++;
            if(method1 != -1) {
                flushMethodVisits(method1, time1, times1);
            }
            if(method2 != -1) {
                flushMethodVisits(method2, time2, times2);
            }
            if(method3 != -1) {
                flushMethodVisits(method3, time3, times3);
            }
            
            if(method4 != -1) {
                flushMethodVisits(method4, time4, times4);
            }

            if(method5 != -1) {
                flushMethodVisits(method5, time5, times5);
            }

            if(method6 != -1) {
                flushMethodVisits(method6, time6, times6);
            }

            for (int i = 0; i < methodIndex; i+=2) {
                long methodId = methodVisits[i];
                methodCountMap.adjustOrPutValue(methodId, 1, 1);

                long time = methodVisits[i+1];
                methodTimeMap.put(methodId, time);
            }

            methodCountMap.forEachEntry(flushMethodsAction);
            methodIndex = 0;

            if(flushMethodsAction.maxMethod>0 && flushMethodsAction.max > times1) {
                method1 = flushMethodsAction.maxMethod;
            }
            if(flushMethodsAction.maxMethod2>0  && flushMethodsAction.max2 > times2) {
                method2 = flushMethodsAction.maxMethod2;
            }
            if(flushMethodsAction.maxMethod3>0 && flushMethodsAction.max3 > times3) {
                method3 = flushMethodsAction.maxMethod3;
            }
            if(flushMethodsAction.maxMethod4>0 && flushMethodsAction.max4 > times4) {
                method4 = flushMethodsAction.maxMethod4;
            }
            if(flushMethodsAction.maxMethod5>0 && flushMethodsAction.max5 > times5) {
                method5 = flushMethodsAction.maxMethod5;
            }
            if(flushMethodsAction.maxMethod6>0 && flushMethodsAction.max6 > times6) {
                method6 = flushMethodsAction.maxMethod6;
            }

            times1 = 0;
            times2 = 0;
            times3 = 0;

            times4 = 0;
            times5 = 0;
            times6 = 0;

            flushMethodsAction.reset();

        }

        private void flushMethodVisits(long methodId, long lastTime, long visits) {
            int cid  = (int)(methodId >> 32);
            int mid = (int) (methodId & 0xFFFFFFFFl);
            Registry.methodVisits[cid].addAndGet(mid, visits);

            Registry.classTouches.lazySet(cid, 1);

            if(methodFirstLines[cid][mid] >= Registry.lineTimes[cid].length()) {
                return;
            }
            Registry.lineTimes[cid].set(methodFirstLines[cid][mid], lastTime);

        }

        public final void flushLineVisits(boolean clear) {

            long[] visits = lineVisits;
            //flushes++;
            for(int i = 0; i < lineIndex;)  {

                long methodId = visits[i];
                long lines = visits[i+1];
                long methodTime = visits[i+2];
                long visitedLines = visits[i+3];
                long methodVisits = visits[i+4];
                flushMethodVisits(methodId, methodTime, methodVisits);
                visits[i+4] = 0;
                flushVisitedLinesTime(methodId, 0, (int)lines, methodTime, visitedLines);

                i+=5;
                for(int l = 0; l < lines; l++,i++)  {

                    long numVisits = visits[i];

                    if(numVisits != 0) {
                        visits[i] = 0;
                        flushLineVisits(numVisits, methodId, l);
                    }
                }


            }
            if(clear) {
                methodIndexMap.clear();
                Arrays.fill(visits, 0);
                lineIndex = 0;
            }
        }

        private void flushLineVisits(long times, long methodId, int lineIndex) {
            if(times == 0) {
                return;
            }
            int cid  = (int)(methodId >> 32);
            int mid = (int) (methodId & 0xFFFFFFFFl);
            AtomicLongArray classVisits = Registry.lineVisits[cid];

            int firstLine = methodFirstLines[cid][mid];
            if(lineIndex+firstLine >= classVisits.length()) {
                return;
            }
            classVisits.addAndGet(lineIndex + firstLine, times);

        }

        private  void flushLineTime(long methodId, int lineIndex, long time) {
            int cid  = (int)(methodId >> 32);
            int mid = (int) (methodId & 0xFFFFFFFFl);
            int firstLine = methodFirstLines[cid][mid];
            AtomicLongArray lineTime = Registry.lineTimes[cid];
            flushLineTime(lineIndex, firstLine, time, lineTime);


        }

        private void flushLineTime(int lineIndex, int firstLine, long time, AtomicLongArray lineTime) {
            int index = lineIndex + firstLine;
            if(index >= lineTime.length()) {
                return;
            }
            lineTime.set(index, time);
        }

        public final long[] visitMultiMethod(long methodId, long time, long visitedLines, int lines) {

            final long[] visits = lineVisits;

            int index = methodIndexMap.putIfAbsent(methodId, lineIndex);

            if(index == -1) {
                index = lineIndex;
                // We might need to flush
                int nextIndex = index + 5 + lines;
                if(nextIndex >= BUFFER_LENGTH) {
                    flushLineVisits(true);
                    index = lineIndex;
                    nextIndex = index + 5 + lines;
                }

                visits[index] = methodId;
                visits[index+1] = lines;

                lineIndex = nextIndex;

            }

            long lastTime = visits[index+2];
            long lastVisitedLines = visits[index+3];
            if(lastTime == 0) {
                visits[index+2] = time;
                visits[index+3] = visitedLines;
            } else if(visitedLines != lastVisitedLines) {
                flushVisitedLinesTime(methodId, visitedLines, lines, lastTime, lastVisitedLines);
                visits[index+2] = time;
                visits[index+3] = visitedLines;
            }  else if(lastTime != time) {
                visits[index+2] = time;
            }
            visits[index+4]++;
            lineCursor = index+5;

            return visits;
        }

        private void flushVisitedLinesTime(long methodId, long visitedLines, int lines, long lastTime, long lastVisitedLines) {
            int cid  = (int)(methodId >> 32);
            int mid = (int) (methodId & 0xFFFFFFFFl);
            int firstLine = methodFirstLines[cid][mid];
            AtomicLongArray lineTime = Registry.lineTimes[cid];
            for(int i = 0; i < lines; i++) {
                long mask = 1 << i;
                if((lastVisitedLines & mask) != 0 && (visitedLines & mask) == 0)  {
                    flushLineTime(i, firstLine, lastTime, lineTime);
                }
            }
        }

        public final void visitLine(long[] visits, int numvisits, int lineIndex) {
            visits[this.lineCursor+lineIndex] += numvisits;
        }

        public final void pushStack(long methodId) {
            if(stacktop == -1) {
                stacktop = methodId;
            }

        }

        public final void popStack(long methodId, long time, int lines) {

            lineCursor+=lines;
            if(stacktop == methodId) {
                lastFlush = time;
                flushMethodVisits();
                flushLineVisits(false);
                stacktop =-1;

                //System.out.println("NUmFlush: " + flushes);
                flushes = 0;
            } else if (time != lastFlush) {
                //lastFlush = time;
                //flushLineVisits();
                //flushMethodVisits();
            }
        }

        @Override
        public void run() {

        }
    }
}
