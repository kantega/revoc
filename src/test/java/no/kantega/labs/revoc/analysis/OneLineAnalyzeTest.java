package no.kantega.labs.revoc.analysis;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
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

        final Map<String, InsnList> methods = new HashMap<String, InsnList>();

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodNode(Opcodes.ASM5, access, name, desc , signature, exceptions) {
                    @Override
                    public void visitEnd() {
                        methods.put(name, instructions);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);


        Map<Integer, Boolean> oneliners = new OneLineAnalyze().analyze(methods.get("run"));

        assertEquals(12, oneliners.size());
        assertTrue(oneliners.get(0));
        assertTrue(oneliners.get(1));
        assertFalse(oneliners.get(2));
        assertFalse(oneliners.get(3));
        assertTrue(oneliners.get(4));
        assertFalse(oneliners.get(5));
        assertFalse(oneliners.get(6));
        assertFalse(oneliners.get(7));
        assertFalse(oneliners.get(8));
        assertFalse(oneliners.get(9));
        assertTrue(oneliners.get(10));
        assertTrue(oneliners.get(11));


    }
}
