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

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

/**
 *
 */
public class SimpleConverageVisitor extends MethodVisitor implements Opcodes {
    private int currentLineNumber;
    private final int classId;
    private final Map<Integer, Integer> classLineNumbers;

    public SimpleConverageVisitor(int classId, Map<Integer, Integer> classLineNumbers, MethodVisitor methodVisitor) {
        super(ASM4, methodVisitor);
        this.classId = classId;
        this.classLineNumbers = classLineNumbers;
    }

    @Override
    public void visitLineNumber(int lineNumber, Label label) {
        // We pass on the debug info
        mv.visitLineNumber(lineNumber, label);
        // Put a reference to our class on the stack
        mv.visitLdcInsn(classId);
        // Put the line number index on the stack (the "n'th" line number)
        mv.visitLdcInsn(classLineNumbers.get(lineNumber));
        // Increment the line in the Registry
        mv.visitMethodInsn(INVOKESTATIC, "no/kantega/labs/revoc/registry/Registry", "registerLineVisited", "(II)V");

    }

    @Override
    public void visitMaxs(int stack, int locals) {
        mv.visitMaxs(stack+1, locals);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
