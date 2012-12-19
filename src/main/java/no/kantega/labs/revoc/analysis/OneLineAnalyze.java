package no.kantega.labs.revoc.analysis;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 */
public class OneLineAnalyze {


    public Map<Integer, Boolean> analyze(InsnList instructions) {

        Map<Label, Integer> label2Line = new HashMap<Label, Integer>();

        Map<Integer, Integer> lineNumber2Index = new TreeMap<Integer, Integer>();

        Map<Integer, Boolean> oneLiners = new HashMap<Integer, Boolean>();


        int currentLine = -1;
        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode ins = instructions.get(i);
            if (ins instanceof LineNumberNode) {
                LineNumberNode node = (LineNumberNode) ins;
                currentLine = node.line;
                label2Line.put(node.start.getLabel(), node.line);
                if (!lineNumber2Index.containsKey(node.line)) {
                    int index = lineNumber2Index.size();
                    lineNumber2Index.put(node.line, index);
                    oneLiners.put(index, Boolean.TRUE);
                }
            } else if(ins instanceof  LabelNode) {
                label2Line.put(((LabelNode)ins).getLabel(),currentLine);
            }
        }

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode ins = instructions.get(i);
            if (ins instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) ins;
                LabelNode target = jump.label;
                int targetIndex = instructions.indexOf(target);
                for(int t = targetIndex; t <= i; t++) {
                    AbstractInsnNode node = instructions.get(t);
                    if(node instanceof LineNumberNode) {
                        LineNumberNode lineNumberNode = (LineNumberNode) node;
                        int lineNumber = lineNumberNode.line;
                        int index = lineNumber2Index.get(lineNumber);
                        oneLiners.put(index, false);
                    } else if(node instanceof LabelNode) {
                        LabelNode label = (LabelNode) node;
                        int lineoflabel = label2Line.get(label.getLabel());
                        int index = lineNumber2Index.get(lineoflabel);
                        oneLiners.put(index, false);
                    }
                }
                for(int t = i; t <= targetIndex; t++) {
                    AbstractInsnNode node = instructions.get(t);
                    if(node instanceof LineNumberNode) {
                        LineNumberNode lineNumberNode = (LineNumberNode) node;
                        int lineNumber = lineNumberNode.line;
                        int index = lineNumber2Index.get(lineNumber);
                        oneLiners.put(index, false);
                    }
                }
            }
        }


        return oneLiners;
    }

}
