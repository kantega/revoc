package no.kantega.labs.revoc.analysis;

import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.util.*;

/**
 *
 */
public class OneLineAnalyze {


    public Map<Integer, BitSet> analyze(MethodNode node) {

        InsnList instructions = node.instructions;


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

            //printDominators(instructions, predecessors);
            if(hash == dominators.hashCode()) {
                break;
            } else {
                hash = dominators.hashCode();
            }


        }


        Map<Integer, BitSet> oneLiners = new TreeMap<>();

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
        }

        for(int i = 0; i < instructions.size(); i++) {
            for (Integer d : dominators.get(instructions.indexOf(instructions.get(i)))) {
                if (instructions.get(d) instanceof LineNumberNode) {
                    int line = ((LineNumberNode) instructions.get(d)).line;
                    int index = lineIndex.get(line);
                    oneLiners.get(i).set(index, true);
                }
            }
        }
        return oneLiners;
    }

    private void addJumpSource(Map<Integer, Set<Integer>> jumpSources, int insn, int successor) {
        if(!jumpSources.containsKey(successor)) {
            jumpSources.put(successor, new HashSet<>());
        }
        jumpSources.get(successor).add(insn);
    }

    private Set<Integer> intersectDominators(Map<Integer, Set<Integer>> dominators, Set<Integer> preds) {
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

}