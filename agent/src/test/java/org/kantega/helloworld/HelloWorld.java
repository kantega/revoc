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

package org.kantega.helloworld;

/**
 *
 */
public class HelloWorld {

    public static void main(String[] args) {
        int oneline = 0;

        int t = 0;
        for (int i = 0; i < 80; i++) {
            t++;
        }

        if (t == 0) {
            System.out.println("blue");
        }

        System.out.println(80);


    }
}
