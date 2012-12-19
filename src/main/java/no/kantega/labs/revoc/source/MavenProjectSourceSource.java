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

package no.kantega.labs.revoc.source;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 *
 */
public class MavenProjectSourceSource implements SourceSource {

    public String[] getSource(String className, ClassLoader classLoader) {
        URL resource = classLoader.getResource(className + ".class");
        if (resource == null) {
            return null;
        }


        try {
            if(resource.getPath() == null) {
                return null;
            }
            File classFile = new File(resource.getPath());

            if (!classFile.exists()) {
                return null;
            }

            String path = classFile.getAbsolutePath();
            File classPathDir = new File(path.substring(0, path.length() - ".class".length() - className.length()));

            File projectRoot = classPathDir;
            while(projectRoot != null && !new File(projectRoot, "src/main/java").exists()) {
                projectRoot = projectRoot.getParentFile();
            }
            File sourceDir = new File(projectRoot, "src/main/java");
            File testSourceDir = new File(projectRoot, "src/test/java");

            for (File dir : new File[]{sourceDir, testSourceDir}) {
                File sourceFile = new File(dir, className + ".java");

                if (sourceFile.exists()) {
                    List<String> lines = FileUtils.readLines(sourceFile);
                    return lines.toArray(new String[lines.size()]);
                }

            }

            return null;


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
