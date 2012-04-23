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
public class HelloWorldLoop {
    public static void main(String[] args) throws InterruptedException {
        while (true) {
            greet();
            Thread.sleep(500);
        }
    }

    private static void greet() {
        System.out.println("Hello World");
    }
}
