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

package no.kantega.labs.helloworld;

/**
 *
 */
public class LongLoop {

    public static void main(String[] args) {
        long before = System.currentTimeMillis();
        long l = 0;
        Thread[] threads = new Thread[100];
        for(int t=0; t < 1; t++) {for (int i = 0; i < threads.length; i++) {
            try {
                threads[i] = new Thread(new RunLoop(LongLoop.class.getMethod("loop")));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }}

        System.out.println(System.currentTimeMillis() - before);
    }

    public static long loop() {
        long l = 0;
        for (int i = 0; i < 200000; i++) {
            l+=innerLoop();
        }
        return l;
    }

    private static long innerLoop() {
        long l = 0;
        for (int i = 0; i < 10; i++) {
            l++;
        }

        l+=fastMethod();
        return l;
    }

    private static long fastMethod() {
       return fasterMethod();
    }

    private static long fasterMethod() {
        return Thread.currentThread().getId();
    }




}

