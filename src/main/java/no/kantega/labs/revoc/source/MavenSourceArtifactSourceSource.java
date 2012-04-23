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

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 *
 */
public class MavenSourceArtifactSourceSource implements SourceSource {
    public String[] getSource(String className, ClassLoader classLoader) {
        URL resource = classLoader.getResource(className + ".class");
        if (resource == null) {
            return null;
        }
        if(!resource.getProtocol().equals("jar")) {
            return  null;
        }
        String filePath = resource.getPath();
        if(!filePath.startsWith("file:") || !filePath.contains("!")) {
            return null;
        }
        filePath = filePath.substring("file:".length());
        filePath = filePath.substring(0, filePath.indexOf("!"));
        File sourceJar = new File(filePath.substring(0, filePath.lastIndexOf(".jar")) +"-sources.jar");
        if(!sourceJar.exists()) {
            return null;
        }
        try {
            JarFile jf = new JarFile(sourceJar);
            String sourcePath = className.replace('.', '/') + ".java";
            ZipEntry entry = jf.getEntry(sourcePath);
            if(entry == null) {
                return null;
            }
            InputStream inputStream = jf.getInputStream(entry);
            List<String> lines = IOUtils.readLines(inputStream);
            return lines.toArray(new String[lines.size()]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
