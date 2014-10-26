package org.kantega.revoc.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.util.*;

/**
 *
 */
public class OneLineAnalyze {


    private final Map<Integer, BitSet> oneLiners;
    private final Map<Integer, BitSet> mustHaveRun;
    private final Map<Integer, BitSet> cantHaveRun;

    private OneLineAnalyze(Map<Integer, BitSet> oneLiners, Map<Integer, BitSet> mustHaveRun, Map<Integer, BitSet> cantHaveRun) {
        this.oneLiners = oneLiners;
        this.mustHaveRun = mustHaveRun;
        this.cantHaveRun = cantHaveRun;
    }

    public boolean mustHaveRunOnce(int exitInstruction, int lineIndex) {
        return oneLiners.get(exitInstruction).get(lineIndex);
    }

    public boolean mustHaveRun(int exitInstruction, int lineIndex) {
        return mustHaveRun.get(exitInstruction).get(lineIndex);
    }

    public boolean cantHaveRun(int exitInstruction, int lineIndex) {
        return cantHaveRun.get(exitInstruction).get(lineIndex);
    }

    public static OneLineAnalyze analyze(MethodNode node) {



        Map<Integer, Set<Integer>> jumpSources = new TreeMap<>();

        Analyzer analyzer = new Analyzer(new BasicInterpreter()) {
            @Override
            protected void newControlFlowEdge(int insn, int successor) {
                addJumpSource(jumpSources, insn, successor);
                super.newControlFlowEdge(insn, successor);

            }

            @Override
            protected boolean newControlFlowExceptionEdge(int insn, int successor) {
                addJumpSource(jumpSources, insn, successor);
                return super.newControlFlowExceptionEdge(insn, successor);

            }

        };

        try {
            analyzer.analyze(node.name, node);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }


        InsnList instructions = node.instructions;

        Map<Integer, Set<Integer>> dominators = new HashMap<>();
        Set<Integer> all = new HashSet<>();
        for (int i = 0; i < instructions.size(); i++) {
            all.add(i);
        }
        for (int i = 0; i < instructions.size(); i++) {
            dominators.put(i, new HashSet<>());

            // Dominator of first is itself
            if(i == 0) {
                dominators.get(i).add(i);
            } else {
                // All other nodes dominate
                dominators.get(i).addAll(new HashSet<>(all));
            }
        }

        int hash = dominators.hashCode();

        Map<Integer, Set<Integer>> predecessors = new TreeMap<>();

        for (int i = 0; i < instructions.size(); i++) {
            predecessors.put(i, new HashSet<>());
        }

        int prehas = predecessors.hashCode();
        while(true) {
            for (int i = 0; i < instructions.size() - 1; i++) {
                AbstractInsnNode ins = instructions.get(i);
                if (ins instanceof JumpInsnNode) {
                    int index = instructions.indexOf(((JumpInsnNode) ins).label);
                    if (ins.getOpcode() == Opcodes.GOTO) {
                        predecessors.get(index).add(i);
                        predecessors.get(index).addAll(predecessors.get(i));
                    } else {
                        predecessors.get(i + 1).add(i);
                        predecessors.get(i + 1).addAll(predecessors.get(i));
                        predecessors.get(index).add(i);
                        predecessors.get(index).addAll(predecessors.get(i));
                    }
                } else {
                    predecessors.get(i + 1).add(i);
                    predecessors.get(i + 1).addAll(predecessors.get(i));

                }
            }

            int newPrehash = predecessors.hashCode();
            if(newPrehash == prehas) {
                break;
            }
            prehas = newPrehash;
        }

        BitSet loops = new BitSet();
        int lineNumber = -1;
        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode ins = instructions.get(i);
            if(ins instanceof LineNumberNode) {
                lineNumber = ((LineNumberNode) ins).line;
            }
            if(lineNumber != -1 && predecessors.get(i).contains(i)) {
                loops.set(lineNumber);
            }
        }
        while (true) {

            for (int i = 1; i < instructions.size(); i++) {
                Set<Integer> doms = new TreeSet<>();
                doms.add(i);

                Set<Integer> preds = new HashSet<>();
                if(jumpSources.containsKey(i)) {
                    preds.addAll(jumpSources.get(i));
                }

                doms.addAll(intersectDominators(dominators, preds));
                dominators.put(i, doms);
            }

            if(hash == dominators.hashCode()) {
                break;
            } else {
                hash = dominators.hashCode();
            }

        }




        Map<Integer, BitSet> oneLiners = new TreeMap<>();
        Map<Integer, BitSet> mustHaveRun = new TreeMap<>();
        Map<Integer, BitSet> cantHaveRun = new TreeMap<>();

        Map<Integer, Integer> lineIndex = new TreeMap<>();

        for(int i = 0; i < instructions.size(); i++) {
            int index = lineIndex.size();

            if(instructions.get(i) instanceof LineNumberNode) {
                int line = ((LineNumberNode) instructions.get(i)).line;
                if(!lineIndex.containsKey(line)) {
                    lineIndex.put(line, index);
                }
            }
            oneLiners.put(i, new BitSet());
            mustHaveRun.put(i, new BitSet());
            cantHaveRun.put(i, new BitSet());
        }



        for(int i = 0; i < instructions.size(); i++) {
            for (Integer d : dominators.get(instructions.indexOf(instructions.get(i)))) {
                if (instructions.get(d) instanceof LineNumberNode) {

                    int line = ((LineNumberNode) instructions.get(d)).line;
                    int index = lineIndex.get(line);
                    if(!loops.get(line)) {
                        oneLiners.get(i).set(index, true);
                    }
                    mustHaveRun.get(i).set(index);

                }
            }
            for(int x = 0; x < instructions.size(); x++) {
                if (instructions.get(x) instanceof LineNumberNode) {
                    if (!predecessors.get(i).contains(x)) {
                        int line = ((LineNumberNode) instructions.get(x)).line;
                        int index = lineIndex.get(line);

                        cantHaveRun.get(i).set(index);
                    }
                }
            }
        }
        return new OneLineAnalyze(oneLiners, mustHaveRun, cantHaveRun);
    }

    private static void addJumpSource(Map<Integer, Set<Integer>> jumpSources, int insn, int successor) {
        if(!jumpSources.containsKey(successor)) {
            jumpSources.put(successor, new HashSet<>());
        }
        jumpSources.get(successor).add(insn);
    }

    private static Set<Integer> intersectDominators(Map<Integer, Set<Integer>> dominators, Set<Integer> preds) {
        if(preds.size() == 0) {
            return Collections.emptySet();
        }
        Set<Integer> intercetions = null;
        for (Integer pred : preds) {
            Set<Integer> dom = dominators.get(pred);
            if(intercetions == null) {
                intercetions = new HashSet<>(dom);
            } else {
                intercetions.retainAll(dom);
            }
        }
        return intercetions;
    }

    public int instructionSize() {
        return oneLiners.size();
    }
}