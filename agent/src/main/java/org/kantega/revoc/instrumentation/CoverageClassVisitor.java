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

package org.kantega.revoc.instrumentation;

import org.kantega.revoc.analysis.OneLineAnalyze;
import org.kantega.revoc.registry.BranchPoint;
import org.kantega.revoc.registry.Registry;
import org.kantega.revoc.registry.ThreadLocalBuffer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 *
 */
public class CoverageClassVisitor extends ClassVisitor implements Opcodes {

    /** Representing this class with an integer which can be used as an array index **/
    private final int classId;

    /** Lines in this class file will have bits set for each line containing line number instructions **/
    private final BitSet existingLines = new BitSet();

    /** Maps actual debug line number to the index of its first usage **/
    private final Map<Integer, Integer> classLineNumbers = new HashMap<Integer, Integer>();


    private final Map<Integer, List<Integer>> methodLineNumbers = new TreeMap<Integer, List<Integer>>();

    private List<String> innerClasses = new ArrayList<String>();

    private List<BranchPoint> branchPoints = new ArrayList<BranchPoint>();
    private String source;
    private String className;

    private boolean trackLines = true;
    private boolean trackTime = true;
    private boolean trackBranches = false;
    private boolean profile = false;
    private boolean profileTime = false;
    private int access;

    private int maxLocalVariableReportLoad = 10000;
    private List<String> methodNames = new ArrayList<String>();
    private List<String> methodDescs = new ArrayList<String>();
    private boolean staticInjected = false;

    private static Set<String> supressedMethodNames = new HashSet<String>();
    static {

        //supressedMethodNames.add("loop");
        //supressedMethodNames.add("innerLoop");
        //supressedMethodNames.add("fastMethod");
        //supressedMethodNames.add("fasterMethod");


    }
    public CoverageClassVisitor(ClassVisitor classVisitor, int classId) {
        super(ASM5, classVisitor);
        this.classId = classId;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        className = name;
        this.access = access;
    }



    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, outerName, innerName, access);
        if(!className.equals(name)) {
            innerClasses.add(name);
        }
    }

    @Override
    public void visitSource(String source, String debug) {
        super.visitSource(source, debug);
        this.source = source;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (!name.startsWith("lambda$") && (access & ACC_SYNTHETIC) != 0) {
            return mv;
        }

        return new FirstPassAnalysis(mv, access, name, desc, signature, exceptions);

    }

    @Override
    public void visitEnd() {


        FieldVisitor lineCounter = super.visitField(ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC + ACC_FINAL, "revoc_counters", "Ljava/util/concurrent/atomic/AtomicLongArray;", null, null);
        lineCounter.visitEnd();
        FieldVisitor timeCounter = super.visitField(ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC + ACC_FINAL, "revoc_times", "Ljava/util/concurrent/atomic/AtomicLongArray;", null, null);
        timeCounter.visitEnd();

        FieldVisitor methodCounter = super.visitField(ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC + ACC_FINAL, "revoc_method_counters", "Lorg/kantega/revoc/registry/UnsafeAtomicLongArray;", null, null);
        methodCounter.visitEnd();
        if(!staticInjected) {
            MethodVisitor mv = super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            visitFetchRevocCounter(mv);
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 0);
            mv.visitEnd();
        }
        super.visitEnd();
    }

    private void visitFetchRevocCounter(MethodVisitor mv) {
        {
            mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "lineVisits", "[Ljava/util/concurrent/atomic/AtomicLongArray;");
            mv.visitLdcInsn(classId);
            mv.visitInsn(AALOAD);
            mv.visitFieldInsn(PUTSTATIC, className, "revoc_counters", "Ljava/util/concurrent/atomic/AtomicLongArray;");
        }
        {
            mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "methodVisits", "[Lorg/kantega/revoc/registry/UnsafeAtomicLongArray;");
            mv.visitLdcInsn(classId);
            mv.visitInsn(AALOAD);
            mv.visitFieldInsn(PUTSTATIC, className, "revoc_method_counters", "Lorg/kantega/revoc/registry/UnsafeAtomicLongArray;");
        }
        {
            mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "lineTimes", "[Ljava/util/concurrent/atomic/AtomicLongArray;");
            mv.visitLdcInsn(classId);
            mv.visitInsn(AALOAD);
            mv.visitFieldInsn(PUTSTATIC, className, "revoc_times", "Ljava/util/concurrent/atomic/AtomicLongArray;");
        }
    }

    protected MethodVisitor createSecondPassAnalyzer(int classId, Map<Integer, Integer> classLineNumbers, Map<Integer, Integer> methodLineNumbers, Map<Integer, Integer> branchPoints, int reportLoad, OneLineAnalyze oneTimeLines, MethodVisitor mv, int access, String name, String desc) {
        return new SecondPassInstrumentation(classId, classLineNumbers, methodLineNumbers, branchPoints, reportLoad, oneTimeLines, mv, access, name, desc);
    }

    public int getClassId() {
        return classId;
    }

    public void setTrackTime(boolean trackTime) {
        this.trackTime = trackTime;
    }

    public void setTrackBranches(boolean trackBranches) {
        this.trackBranches = trackBranches;
    }

    public Map<Integer, List<Integer>> getMethodLineNumbers() {
        return methodLineNumbers;
    }

    /**
     * Runs a first pass of the code such that instrumentation can be done on the basis of class analysis.
     */
    class FirstPassAnalysis extends MethodNode {

        private final MethodVisitor mv;

        public FirstPassAnalysis(MethodVisitor mv, int access, String name, String desc, String signature, String[] exceptions) {
            super(Opcodes.ASM5, access, name, desc, signature, exceptions);
            this.mv = mv;
        }

        @Override
        public void visitEnd() {

            if(supressedMethodNames.contains(name) || (access & Opcodes.ACC_ABSTRACT) != 0 ) {
                accept(new MethodVisitor(ASM5, mv) {
                });
            } else {

                final Map<Integer, Integer> methodLineNumbers = analyzeLinePoints(instructions);

                final Map<Integer, Integer> branchPoints = analyzeBranchPoints(instructions);

                final OneLineAnalyze oneTimeLines = OneLineAnalyze.analyze(this);

                int numExitPoints = countExitPoints(instructions);
                int reportLoad = (methodLineNumbers.size() + branchPoints.size()) * numExitPoints;

                accept(createSecondPassAnalyzer(classId, classLineNumbers, methodLineNumbers, branchPoints, reportLoad, oneTimeLines, mv, access, name, desc));
                methodNames.add(name);
                methodDescs.add(desc);
            }



        }




        private int countExitPoints(InsnList instructions) {
            int numberOfReturns = 0;
            for (int i = 0; i < instructions.size(); i++) {
                AbstractInsnNode ins = instructions.get(i);
                if (ins instanceof InsnNode) {
                    InsnNode node = (InsnNode) ins;
                    if((node.getOpcode() >= IRETURN && node.getOpcode() <= RETURN ) || node.getOpcode() == ATHROW) {
                        numberOfReturns ++;
                    }
                }
            }
            return numberOfReturns;

        }

        private Map<Integer, Integer> analyzeBranchPoints(InsnList instructions) {
            int currentLineNumber = 0;
            final Map<Integer, Integer> branchPoints = new TreeMap<Integer, Integer>();
            for (int i = 0; i < instructions.size(); i++) {
                AbstractInsnNode ins = instructions.get(i);
                if (ins instanceof LineNumberNode) {
                    LineNumberNode node = (LineNumberNode) ins;
                    currentLineNumber = node.line;
                }

                if (ins instanceof JumpInsnNode) {
                    JumpInsnNode node = (JumpInsnNode) ins;


                    if (node.getOpcode() != Opcodes.GOTO && node.getOpcode() != Opcodes.JSR) {
                        int globalIndex = CoverageClassVisitor.this.branchPoints.size();

                        CoverageClassVisitor.this.branchPoints.add(new BranchPoint(node.getOpcode(), currentLineNumber - 1));

                        branchPoints.put(branchPoints.size(), globalIndex);
                    }
                }
            }
            return branchPoints;
        }

        private Map<Integer, Integer> analyzeLinePoints(InsnList instructions) {
            CoverageClassVisitor.this.methodLineNumbers.put(methodNames.size(), new ArrayList<Integer>());
            final Map<Integer, Integer> methodLineNumbers = new TreeMap<Integer, Integer>();

            for (int i = 0; i < instructions.size(); i++) {
                AbstractInsnNode ins = instructions.get(i);
                if (ins instanceof LineNumberNode) {
                    LineNumberNode node = (LineNumberNode) ins;
                    int lineNumber = node.line;
                    existingLines.set(lineNumber);
                    int idx = classLineNumbers.size();
                    if (!classLineNumbers.containsKey(lineNumber)) {
                        classLineNumbers.put(lineNumber, idx);
                    } else {
                        idx = classLineNumbers.get(lineNumber);
                    }
                    if (!methodLineNumbers.containsKey(lineNumber)) {
                        methodLineNumbers.put(lineNumber, methodLineNumbers.size());
                    }

                    List<Integer> lines = CoverageClassVisitor.this.methodLineNumbers.get(methodNames.size());
                    if(!lines.contains(idx)) {
                        lines.add(idx);
                    }

                }
            }
            return methodLineNumbers;
        }

    }

    class SecondPassInstrumentation extends AdviceAdapter {


        private final int classId;
        private final Map<Integer, Integer> classLineNumbers;
        private final Map<Integer, Integer> methodLineNumbers;
        private final Map<Integer, Integer> branchPoints;
        private final OneLineAnalyze oneTimeLines;
        private final int access;
        private final String name;
        private int methodJumpIndex = 0;
        private int timeLocal;

        private final boolean useLocalVariables;
        private int lineVisitsLocalVariable;
        private int timeVisitsLocalVariable;
        private int beforeBranchPointsLocalVariable;
        private int afterBranchPointsLocalVariable;
        private int frameMapLocalVariable;
        //private int waitTimeLocalVariable;
        //private int totalWaitTimeLocalVariable;
        private int startTimeLocalVariable;
        private boolean profile;
        private int threadBufferLocal;
        private boolean constructor;
        private int multiMethodCursorLocalVariable;

        protected SecondPassInstrumentation(int classId, Map<Integer, Integer> classLineNumbers, Map<Integer, Integer> methodLineNumbers, Map<Integer, Integer> branchPoints, int reportLoad, OneLineAnalyze oneTimeLines, MethodVisitor methodVisitor, int access, String name, String desc) {
            super(ASM5, methodVisitor, access, name, desc);
            this.classId = classId;
            this.classLineNumbers = classLineNumbers;
            this.methodLineNumbers = methodLineNumbers;
            this.branchPoints = branchPoints;
            this.oneTimeLines = oneTimeLines;
            this.access = access;
            this.name = name;
            this.useLocalVariables = reportLoad <= maxLocalVariableReportLoad;
            this.profile = CoverageClassVisitor.this.profile && !"<clinit>".equals(name);
        }

        // Maps absolute line number to local variable index
        private Map<Integer, Integer> lineNumberLocalVariables = new TreeMap<Integer, Integer>();
        private Map<Integer, Integer> beforeBranchPointLocalVariables = new TreeMap<Integer, Integer>();
        private Map<Integer, Integer> afterBranchPointLocalVariables = new TreeMap<Integer, Integer>();
        public Label before;
        public Label handler;


        int insIdx = 0;

        @Override
        public void visitCode() {
            super.visitCode();

            insIdx = 0;

            if(name.equals("<clinit>") && (access & ACC_STATIC) != 0) {
                staticInjected = true;
                visitFetchRevocCounter(mv);
            }



            if(trackLines) {
                if(useLocalVariables) {
                    initializeLineNumberLocalVariables();
                    multiMethodCursorLocalVariable = newLocal(Type.INT_TYPE);
                } else {
                    initializeLineNumerArrayLocalVariable();
                }
            }

            if(trackBranches) {
                if(useLocalVariables) {
                    initializeBranchPointLocalVariables();
                } else {
                    initializeBranchPointArrayLocalVariable();
                }
            }

            if(profile) {
                mv.visitLdcInsn((long)classId << 32 | (long) methodNames.size());
                mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerMethodEnter", "(J)Lorg/kantega/revoc/registry/Registry$FrameMap;");
                mv.visitVarInsn(ASTORE, frameMapLocalVariable = newLocal(Type.getType(Registry.FrameMap.class)));
                initalizeProfilingLocalVariables();
            }
            if(methodLineNumbers.size() == 1) {
                mv.visitLdcInsn((long) classId << 32 | (long) methodNames.size());
                mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerOneLineVisited", "(J)V");
            }else if(methodLineNumbers.size() > 1 ) {
                mv.visitLdcInsn((long)classId << 32 | (long) methodNames.size());
                mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "getThreadLocalBufferInc", "(J)Lorg/kantega/revoc/registry/ThreadLocalBuffer;");
                threadBufferLocal = newLocal(Type.getType(ThreadLocalBuffer.class));
                mv.visitVarInsn(ASTORE, threadBufferLocal);

            }
            if (name.equals("<init>")) {
                constructor = true;
            } else {
                startTryBlock();
            }
        }
        private void startTryBlock() {
            before = new Label();
            handler = new Label();
            mv.visitLabel(before);
        }

        private void initalizeProfilingLocalVariables() {
            if(profileTime) {
                nanoTime();
                mv.visitVarInsn(LSTORE, startTimeLocalVariable = newLocal(Type.getType("J")));
            }
            //mv.visitInsn(LCONST_0);
            //mv.visitVarInsn(LSTORE, totalWaitTimeLocalVariable = newLocal(Type.getType("J")));
            //mv.visitInsn(LCONST_0);
            //mv.visitVarInsn(LSTORE, waitTimeLocalVariable = newLocal(Type.getType("J")));
        }

        private void nanoTime() {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J");
            //mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "time", "J");
            //mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "nanotime", "J");
            //mv.visitInsn(Opcodes.LCONST_0);

        }

        private void initializeBranchPointArrayLocalVariable() {
            visitIntConstantInstruction(branchPoints.size());
            mv.visitIntInsn(NEWARRAY, T_INT);
            mv.visitVarInsn(ASTORE, beforeBranchPointsLocalVariable = newLocal(Type.getType("[I")));
            visitIntConstantInstruction(branchPoints.size());
            mv.visitIntInsn(NEWARRAY, T_INT);
            mv.visitVarInsn(ASTORE, afterBranchPointsLocalVariable = newLocal(Type.getType("[I")));
        }

        private void initializeLineNumerArrayLocalVariable() {
            visitIntConstantInstruction(methodLineNumbers.size());
            mv.visitIntInsn(NEWARRAY, T_INT);
            mv.visitVarInsn(ASTORE, lineVisitsLocalVariable = newLocal(Type.getType("[I")));
            if(trackTime) {
                visitIntConstantInstruction(methodLineNumbers.size());
                mv.visitIntInsn(NEWARRAY, T_LONG);
                mv.visitVarInsn(ASTORE, timeVisitsLocalVariable = newLocal(Type.getType("[J")));
            }


        }

        private void initializeBranchPointLocalVariables() {
            for (int branchIndex : branchPoints.keySet()) {
                {
                    mv.visitInsn(ICONST_0);
                    int local = newLocal(Type.INT_TYPE);
                    beforeBranchPointLocalVariables.put(branchIndex, local);
                    mv.visitVarInsn(ISTORE, local);
                }
                {
                    mv.visitInsn(ICONST_0);
                    int local = newLocal(Type.INT_TYPE);
                    afterBranchPointLocalVariables.put(branchIndex, local);
                    mv.visitVarInsn(ISTORE, local);
                }
            }
        }

        private void initializeLineNumberLocalVariables() {
            if(methodLineNumbers.size() > 1) {

                for (int lineNumber : methodLineNumbers.keySet()) {
                    {
                        visitIntConstantInstruction(-1);
                        int local = newLocal(Type.INT_TYPE);
                        lineNumberLocalVariables.put(lineNumber, local);
                        mv.visitVarInsn(ISTORE, local);
                    }
                }
            }
        }


        @Override
        public void visitLineNumber(int lineNumber, Label label) {
            insIdx++;
            mv.visitLineNumber(lineNumber, label);
            if(trackLines) {
                if(useLocalVariables) {
                    if(methodLineNumbers.size() > 1) {
                        mv.visitIincInsn(lineNumberLocalVariables.get(lineNumber), 1);
                    }
                } else {
                    {
                    mv.visitVarInsn(ALOAD, lineVisitsLocalVariable);
                    visitIntConstantInstruction(methodLineNumbers.get(lineNumber));
                    mv.visitInsn(DUP2);
                    mv.visitInsn(IALOAD);
                    mv.visitInsn(ICONST_1);
                    mv.visitInsn(IADD);
                    mv.visitInsn(IASTORE);
                    }
                    if(trackTime) {

                        mv.visitVarInsn(ALOAD, timeVisitsLocalVariable);
                        visitIntConstantInstruction(methodLineNumbers.get(lineNumber));
                        mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "time", "J");
                        mv.visitInsn(LASTORE);


                    }
                }


            }

        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            insIdx++;
            /*
            if(profile && isWaitMethod(opcode, owner, name, desc)) {
                nanoTime();
                mv.visitVarInsn(LSTORE, waitTimeLocalVariable);

            } */
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if(constructor && name.equals("<init>")) {
                constructor = false;
                startTryBlock();
            }
            /*
            if(profile && isWaitMethod(opcode, owner, name, desc)) {
                nanoTime();
                mv.visitVarInsn(LLOAD, waitTimeLocalVariable);
                mv.visitInsn(LSUB);
                mv.visitVarInsn(LLOAD, totalWaitTimeLocalVariable);
                mv.visitInsn(LADD);
                mv.visitVarInsn(LSTORE, totalWaitTimeLocalVariable);

            }
            */
            if(trackTime) {
                //updateTime();
            }
        }

        private boolean isWaitMethod(int opcode, String owner, String name, String desc) {
            return opcode == INVOKEVIRTUAL && "java/lang/Thread".equals(owner) && "join".equals(name) && "()V".equals(desc);
        }

        @Override
        public void visitInsn(int i) {

            if (trackTime && i >= IRETURN && i <= RETURN) {
                //updateTime();
            }
            super.visitInsn(i);
            insIdx++;
        }

        private void updateTime() {
            if(trackTime) {
                mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "time", "J");
                mv.visitVarInsn(LSTORE, timeLocal);
            }
        }

        /**
         * Visit a BIPUSH, SIPUSH or LDC instruction based on the size of num
         *
         * @param num the int constant to put on the stack
         */
        private void visitIntConstantInstruction(int num) {
            if (num <= 5) {
                mv.visitInsn(ICONST_0 + num);
            } else if (num < 128) {
                mv.visitIntInsn(BIPUSH, num);
            } else if (num <= Short.MAX_VALUE) {
                mv.visitIntInsn(SIPUSH, num);
            } else {
                mv.visitLdcInsn(num);
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if(methodLineNumbers.size() > 1) {
                mv.visitTryCatchBlock(before, handler, handler, null);
                mv.visitLabel(handler);
                generateLineVisitRegistration(true, -1);
                mv.visitInsn(ATHROW);
            }
            mv.visitMaxs(maxStack, maxLocals + lineNumberLocalVariables.size() + beforeBranchPointLocalVariables.size() + afterBranchPointLocalVariables.size());
        }

        @Override
        protected void onMethodExit(int opcode) {
            int myIndex = insIdx;
            if (opcode != ATHROW && methodLineNumbers.size() > 1) {
                generateLineVisitRegistration(false, myIndex);
            }
        }

        @Override
        public void visitJumpInsn(int i, Label label) {
            insIdx++;
            if (!trackBranches || i == Opcodes.GOTO || i == Opcodes.JSR ) {
                super.visitJumpInsn(i, label);
            } else {
                int index = methodJumpIndex;

                if(useLocalVariables) {
                    {
                        int local = beforeBranchPointLocalVariables.get(index);
                        mv.visitIincInsn(local, 1);
                    }
                    super.visitJumpInsn(i, label);

                    {
                        int local = afterBranchPointLocalVariables.get(index);
                        mv.visitIincInsn(local, 1);
                    }
                } else {
                     {
                         mv.visitVarInsn(ALOAD, beforeBranchPointsLocalVariable);
                         visitIntConstantInstruction(index);
                         mv.visitInsn(DUP2);
                         mv.visitInsn(IALOAD);
                         mv.visitInsn(ICONST_1);
                         mv.visitInsn(IADD);
                         mv.visitInsn(IASTORE);
                     }
                    super.visitJumpInsn(i, label);
                    {
                        mv.visitVarInsn(ALOAD, afterBranchPointsLocalVariable);
                        visitIntConstantInstruction(index);
                        mv.visitInsn(DUP2);
                        mv.visitInsn(IALOAD);
                        mv.visitInsn(ICONST_1);
                        mv.visitInsn(IADD);
                        mv.visitInsn(IASTORE);
                    }

                }
                methodJumpIndex++;
            }
        }

        private void generateLineVisitRegistration(boolean isCatchBlock, int myIndex) {

            if(profile) {
                mv.visitVarInsn(ALOAD, frameMapLocalVariable);
                if(profileTime) {
                    nanoTime();
                    mv.visitVarInsn(LLOAD, startTimeLocalVariable);
                    //mv.visitVarInsn(LLOAD, totalWaitTimeLocalVariable);
                    mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerMethodExit", "(Lorg/kantega/revoc/registry/Registry$Frame;JJ)V");
                } else {
                    mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerMethodExit", "(Lorg/kantega/revoc/registry/Registry$Frame;)V");
                }
            }

            // Get the int[] for this class


            if(trackLines) {

                {


                    if(trackTime) {
                        /*
                        mv.visitFieldInsn(GETSTATIC, "org/kantega/revoc/registry/Registry", "lineTimes", "[Ljava/util/concurrent/atomic/AtomicLongArray;");
                        visitIntConstantInstruction(classId);
                        mv.visitInsn(AALOAD);
                        */
                    }

                }

                if(useLocalVariables) {

                    if(methodLineNumbers.size() != 1) {

                        mv.visitVarInsn(ALOAD, threadBufferLocal);
                        mv.visitLdcInsn(((long) classId << 32 | (long) methodNames.size()));

                        long initialMask = 0;

                        if(!isCatchBlock) {
                            for (Integer lineNumber : lineNumberLocalVariables.keySet()) {
                                int lineIndex = methodLineNumbers.get(lineNumber);
                                if (lineIndex < 64) {
                                    if (oneTimeLines.mustHaveRun(myIndex, lineIndex)) {
                                        initialMask |= (1l << lineIndex);
                                    }
                                }
                            }
                        }
                        mv.visitLdcInsn(initialMask);
                        for (Integer lineNumber : lineNumberLocalVariables.keySet()) {
                            int lineIndex = methodLineNumbers.get(lineNumber);
                            if(lineIndex >= 64) {
                                continue;
                            }
                            if(isCatchBlock) {
                                continue;
                            }
                            if(oneTimeLines.cantHaveRun(myIndex, lineIndex)) {
                                continue;
                            }
                            if(oneTimeLines.mustHaveRun(myIndex, lineIndex)) {
                                continue;
                            }

                            mv.visitVarInsn(ILOAD, lineNumberLocalVariables.get(lineNumber));
                            mv.visitInsn(ICONST_M1);
                            Label after = new Label();
                            mv.visitJumpInsn(IF_ICMPEQ, after);
                            mv.visitLdcInsn(1l << lineIndex);
                            mv.visitInsn(LOR);
                            mv.visitLabel(after);

                        }

                                    visitIntConstantInstruction(Math.min(64, lineNumberLocalVariables.size()));
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/kantega/revoc/registry/ThreadLocalBuffer", "visitMultiMethod", "(JJI)I");
                        mv.visitVarInsn(ISTORE, multiMethodCursorLocalVariable);

                        for (Integer lineNumber : lineNumberLocalVariables.keySet()) {
                            int lineIndex = methodLineNumbers.get(lineNumber);
                            if (isCatchBlock || !oneTimeLines.mustHaveRunOnce(myIndex, lineIndex)) {
                                if (lineIndex < 64) {
                                    mv.visitVarInsn(ALOAD, threadBufferLocal);
                                    mv.visitVarInsn(ILOAD, multiMethodCursorLocalVariable);
                                    if(!isCatchBlock && oneTimeLines.cantHaveRun(myIndex, lineIndex)) {
                                        mv.visitInsn(ICONST_M1);
                                    } else {
                                        mv.visitVarInsn(ILOAD, lineNumberLocalVariables.get(lineNumber));
                                    }
                                    visitIntConstantInstruction(lineIndex);
                                    mv.visitLdcInsn((long) classId << 32 | (long) methodNames.size());
                                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/kantega/revoc/registry/ThreadLocalBuffer", "visitLine", "(IIIJ)V");
                                }
                            }
                        }
                        for (Integer lineNumber : lineNumberLocalVariables.keySet()) {
                            int lineIndex = methodLineNumbers.get(lineNumber);

                            if(lineIndex >= 64) {
                                mv.visitVarInsn(ALOAD, threadBufferLocal);
                                mv.visitVarInsn(ILOAD, lineNumberLocalVariables.get(lineNumber));
                                visitIntConstantInstruction(lineIndex);
                                mv.visitLdcInsn((long) classId << 32 | (long) methodNames.size());
                                mv.visitMethodInsn(INVOKEVIRTUAL, "org/kantega/revoc/registry/ThreadLocalBuffer", "visitLine", "(IIJ)V");
                            }
                        }

                        mv.visitVarInsn(ALOAD, threadBufferLocal);
                        mv.visitLdcInsn((long) classId << 32 | (long) methodNames.size());
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/kantega/revoc/registry/ThreadLocalBuffer", "popStack", "(J)V");

                    }
                } else {


                    mv.visitFieldInsn(GETSTATIC, className, "revoc_counters", "Ljava/util/concurrent/atomic/AtomicLongArray;");
                    if(trackTime) {
                        mv.visitFieldInsn(GETSTATIC, className, "revoc_times", "Ljava/util/concurrent/atomic/AtomicLongArray;");
                    }
                    mv.visitVarInsn(ALOAD, lineVisitsLocalVariable);
                    if(trackTime) {
                        mv.visitVarInsn(ALOAD, timeVisitsLocalVariable);
                    }
                    visitIntConstantInstruction(classLineNumbers.get(methodLineNumbers.keySet().iterator().next()));

                    if(trackTime) {
                        mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerLineTimeVisitedArray", "(Ljava/util/concurrent/atomic/AtomicLongArray;Ljava/util/concurrent/atomic/AtomicLongArray;[I[JI)V");
                    } else {
                        mv.visitMethodInsn(INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerLineVisitedArray", "(Ljava/util/concurrent/atomic/AtomicLongArray;[II)V");
                    }

                }
            }

            if(trackBranches && !branchPoints.isEmpty()) {
                if(useLocalVariables) {
                    for(Integer index :branchPoints.keySet()) {

                        visitIntConstantInstruction(classId);
                        visitIntConstantInstruction(branchPoints.get(index));
                        mv.visitVarInsn(ILOAD, beforeBranchPointLocalVariables.get(index));
                        mv.visitVarInsn(ILOAD, afterBranchPointLocalVariables.get(index));
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerBranchPointVisits", "(IIII)V");

                    }
                } else {
                    visitIntConstantInstruction(classId);
                    mv.visitVarInsn(ALOAD, beforeBranchPointsLocalVariable);
                    mv.visitVarInsn(ALOAD, afterBranchPointsLocalVariable);
                    visitIntConstantInstruction(branchPoints.get(branchPoints.keySet().iterator().next()));
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/kantega/revoc/registry/Registry", "registerBranchPointVisitsArray", "(I[I[II)V");
                }
            }
        }

        @Override
        public void visitLabel(Label label) {
            insIdx++;
            super.visitLabel(label);

        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            insIdx++;
            super.visitVarInsn(opcode, var);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            insIdx++;
            super.visitFrame(type, nLocal, local, nStack, stack);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            insIdx++;
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            insIdx++;
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            insIdx++;
            super.visitLdcInsn(cst);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            insIdx++;
            super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            insIdx++;
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            insIdx++;
            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            insIdx++;
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            insIdx++;
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }
    }



    public BitSet getExistingLines() {
        return existingLines;
    }

    public int[] getLineIndexes() {
        Map<Integer, Integer> index2Line = new TreeMap<Integer, Integer>();

        for (Integer lineNum : classLineNumbers.keySet()) {
            index2Line.put(classLineNumbers.get(lineNum), lineNum);
        }
        int[] lines = new int[index2Line.size()];
        int c = 0;
        for (Integer index : index2Line.keySet()) {
            lines[c++] = index2Line.get(index);
        }
        return lines;
    }

    public List<BranchPoint> getBranchPoints() {
        return branchPoints;
    }

    public String getSource() {
        return source;
    }

    public String getClassName() {
        return className;
    }

    public boolean isInterface() {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

    public boolean isEnum() {
        return (access & Opcodes.ACC_ENUM) != 0;
    }

    public List<String> getInnerClasses() {
        return innerClasses;
    }

    public List<String> getMethodNames() {
        return methodNames;
    }

    public List<String> getMethodDescs() {
        return methodDescs;
    }
}
