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

package no.kantega.labs.revoc.demo;

import no.kantega.labs.helloworld.HelloWorld;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class ControlFlowAnalysis {

    public static void main(String[] args) throws IOException, AnalyzerException {
        InputStream inputStream = HelloWorld.class.getResourceAsStream("ClassWithLogic.class");

        ClassReader cr = new ClassReader(inputStream);

        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        MethodNode mn = (MethodNode) classNode.methods.get(1);

        System.out.println("Analyzing method " + mn.name + ":");

        Analyzer a = new Analyzer(new BasicInterpreter()) {
            protected Frame newFrame(int nLocals, int nStack) {
                return new Node(nLocals, nStack);
            }

            protected Frame newFrame(Frame src) {
                return new Node(src);
            }

            @Override
            protected void newControlFlowEdge(int src, int dst) {
                Node s = (Node) getFrames()[src];
                Node d = (Node) getFrames()[dst];
                s.successors.add(d);
            }

            protected void newControlFlowEdge(Frame src, Frame dst) {
                Node s = (Node) src;
                s.successors.add(new Node(dst));
            }
        };
        a.analyze(classNode.name, mn);

        Frame[] frames = a.getFrames();
        int edges = 0;
        int nodes = 0;
        for (int i = 0; i < frames.length; ++i) {

            if (frames[i] != null) {
                int numSuccessors = ((Node) frames[i]).successors.size();
                edges += numSuccessors;
                nodes += 1;
                TraceMethodVisitor v = new TraceMethodVisitor(new Textifier());
                mn.instructions.get(i).accept(v);
                System.out.println("Frame " + i +" (" + numSuccessors +") " + v.toString());
            }
        }

        System.out.println("CC: " + (edges - nodes + 2));

    }
}

class Node extends Frame {
    Set<no.kantega.labs.revoc.demo.Node> successors = new HashSet<no.kantega.labs.revoc.demo.Node>();

    public Node(int nLocals, int nStack) {
        super(nLocals, nStack);
    }

    public Node(Frame src) {
        super(src);
    }
}