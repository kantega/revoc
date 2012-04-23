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
import no.kantega.labs.revoc.instrumentation.CoverageClassVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

/**
 *
 */
public class HelloWorldByteCode {

    public static void main(String[] args) throws IOException {


        Class clazz = HelloWorld.class;
        InputStream inputStream = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");

        ClassReader reader = new ClassReader(inputStream);

        ClassVisitor visitor = new TraceClassVisitor(new PrintWriter(System.out));
        reader.accept(new CoverageClassVisitor(visitor, 0), ClassReader.EXPAND_FRAMES);
    }
}
