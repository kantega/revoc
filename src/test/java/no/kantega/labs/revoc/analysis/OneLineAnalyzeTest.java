package no.kantega.labs.revoc.analysis;

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

    @Test
    public void shouldIdentifyOneliners() throws IOException {
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
        Map<Integer, BitSet> oneliners = new OneLineAnalyze().analyze(run);
        assertEquals(run.instructions.size(), oneliners.size());

        BitSet fromReturn = oneliners.get(oneliners.size()-2);
        assertTrue(fromReturn.get(0));
        assertTrue(fromReturn.get(1));
        assertTrue(fromReturn.get(2));
        assertFalse(fromReturn.get(3));
        assertTrue(fromReturn.get(4));
        assertFalse(fromReturn.get(5));
        assertFalse(fromReturn.get(6));
        assertFalse(fromReturn.get(7));
        assertFalse(fromReturn.get(8));
        assertFalse(fromReturn.get(9));
        assertTrue(fromReturn.get(10));
        assertTrue(fromReturn.get(11));

    }
}
