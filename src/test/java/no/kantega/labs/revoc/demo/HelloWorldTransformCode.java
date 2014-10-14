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
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import static no.kantega.labs.revoc.demo.ClassUtils.invokeMainMethodUsingReflection;

/**
 *
 */
public class HelloWorldTransformCode {

    public static void main(String[] args) throws IOException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {


        InputStream inputStream = HelloWorld.class.getResourceAsStream("HelloWorld.class");

        ClassReader reader = new ClassReader(inputStream);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        reader.accept(new HelloClassTransformer(writer), ClassReader.EXPAND_FRAMES);

        invokeMainMethodUsingReflection("no.kantega.labs.helloworld.HelloWorld", writer.toByteArray());
    }


}

class HelloClassTransformer extends ClassVisitor {

    public HelloClassTransformer(ClassVisitor classVisitor) {
        super(Opcodes.ASM5, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
            @Override
            public void visitLdcInsn(Object o) {
                if (o != null && o instanceof String && o.toString().equals("Hello world")) {
                    super.visitLdcInsn("Hello Norway");
                } else {
                    super.visitLdcInsn(o);
                }
            }
        };
    }
}