package no.kantega.labs.revoc.analysis;

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class OneLineAnalyzeTest {

    private OneLineAnalyze analyze;
    private InsnList instructions;

    @Before
    public void before() throws IOException {
        ClassReader reader = new ClassReader(getClass().getResourceAsStream("OneLiner.class"));

        final Map<String, MethodNode> methods = new HashMap<>();

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodNode(Opcodes.ASM5, access, name, desc , signature, exceptions) {
                    @Override
                    public void visitEnd() {
                        methods.put(name, this);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);


        MethodNode run = methods.get("run");
        instructions = run.instructions;
        analyze = OneLineAnalyze.analyze(run);
    }

    @Test
    public void shouldIdentifyOneliners() throws IOException {


        int exitInstruction = analyze.instructionSize()-2;
        assertTrue(analyze.mustHaveRunOnce(exitInstruction, 0));
        assertTrue(analyze.mustHaveRunOnce(exitInstruction, 1));
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 2));// In a loop
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 3));
        assertTrue(analyze.mustHaveRunOnce(exitInstruction, 4));
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 5));
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 6));
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 7));
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 8));
        assertFalse(analyze.mustHaveRunOnce(exitInstruction, 9));
        assertTrue(analyze.mustHaveRunOnce(exitInstruction, 11));
        assertTrue(analyze.mustHaveRunOnce(exitInstruction, 12));

    }

    @Test
    public void shouldIdentifyMustHaveRun() throws IOException {

        int exitInstruction = analyze.instructionSize()-2;
        
        assertTrue(analyze.mustHaveRun(exitInstruction, 0));
        assertTrue(analyze.mustHaveRun(exitInstruction, 1));
        assertTrue(analyze.mustHaveRun(exitInstruction, 2)); // In a loop
        assertFalse(analyze.mustHaveRun(exitInstruction, 3));
        assertTrue(analyze.mustHaveRun(exitInstruction, 4));
        assertFalse(analyze.mustHaveRun(exitInstruction, 5));
        assertFalse(analyze.mustHaveRun(exitInstruction, 6));
        assertFalse(analyze.mustHaveRun(exitInstruction, 7));
        assertFalse(analyze.mustHaveRun(exitInstruction, 8));
        assertFalse(analyze.mustHaveRun(exitInstruction, 9));
        assertTrue(analyze.mustHaveRun(exitInstruction, 11));
        assertTrue(analyze.mustHaveRun(exitInstruction, 12));

    }

    @Test
    public void shouldIdentifyCantHaveRun() throws IOException {

        int exitInstruction = 72;


        for(int i = 0; i < 12; i++) {
            if(i < 11) {
                assertFalse("Line index " + i + " should be runnable when exiting from instruction " + exitInstruction, analyze.cantHaveRun(exitInstruction, i));
            } else {
                assertTrue(analyze.cantHaveRun(exitInstruction, i));
            }
        }
    }
}
