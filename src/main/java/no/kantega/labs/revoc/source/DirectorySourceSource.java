package no.kantega.labs.revoc.source;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 *
 */
public class DirectorySourceSource implements SourceSource {
    private Map<String, File> sourceMap;
    private Map<String, JarFile> jarFiles = new HashMap<String, JarFile>();

    public DirectorySourceSource() {
        String revSources = System.getenv("REVOC_SOURCES");
        sourceMap = new HashMap<String, File>();

        if (revSources != null) {
            File revSourceDirectory = new File(revSources);

            if (revSourceDirectory.exists()) {
                File[] children = revSourceDirectory.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.getName().endsWith(".jar");
                    }
                });

                if (children != null) {
                    sourceMap.putAll(parseSources(children));
                }
            }
        }
    }

    private Map<String, File> parseSources(File[] files) {
        HashMap<String, File> index = new HashMap<String, File>();
        for (File file : files) {
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(file);
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".java")) {

                        index.put(entry.getName(), file);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {

                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        }
        return index;
    }

    @Override
    public String[] getSource(String className, ClassLoader classLoader) {
        try {
            String sourceFileName = className.replace('.', '/') + ".java";
            File file = sourceMap.get(sourceFileName);
            if(file == null) {
                return null;
            }

            JarFile jarFile = getJarFile(file);
            ZipEntry entry = jarFile.getEntry(sourceFileName);
            if (entry == null) {
                return null;
            }
            InputStream inputStream = jarFile.getInputStream(entry);
            List<String> lines = IOUtils.readLines(inputStream);
            return lines.toArray(new String[lines.size()]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private synchronized JarFile getJarFile(File file) {
        JarFile jarFile = jarFiles.get(file.getAbsolutePath());
        if(jarFile == null) {
            try {
                jarFiles.put(file.getAbsolutePath(), jarFile = new JarFile(file));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return jarFile;
    }
}
