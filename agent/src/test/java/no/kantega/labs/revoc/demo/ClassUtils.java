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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ClassUtils {

    public static void invokeMainMethodUsingReflection(String name, byte[] bytes) throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        invokeMainMethodUsingReflection(name, bytes, 1);
    }
    public static void invokeMainMethodUsingReflection(String className, byte[] bytes, int times) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class helloWorldClass = getClassFromBytes(className, bytes);

        Method mainMethod = helloWorldClass.getMethod("main", (new String[0]).getClass());
        for(int i = 0 ; i < times; i++) {
            long before = System.nanoTime();
            mainMethod.invoke(null, new Object[]{new String[]{}});
            System.out.println(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
        }
    }

    public static Class<?> getClassFromBytes(String className, byte[] bytes) throws ClassNotFoundException {
        return new InMemoryClassLoader(className, bytes).loadClass(className);
    }

    private static class InMemoryClassLoader extends ClassLoader {

        private final String className;
        private final byte[] bytes;

        public InMemoryClassLoader(String className, byte[] bytes) {

            this.className = className;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> findClass(String s) throws ClassNotFoundException {
            if(s.equals(className)) {
                return defineClass(s, bytes, 0, bytes.length);
            }

            throw new ClassNotFoundException(s);
        }

        @Override
        protected Class<?> loadClass(String s, boolean b) throws ClassNotFoundException {
            try {
                return findClass(s);
            } catch (ClassNotFoundException e) {
                return super.loadClass(s, b);
            }

        }
    }
}
