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
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 *
 */
public class MavenSourceArtifactSourceSource implements SourceSource {

    private final Map<String, MavenSourceInfo> mavenInfoMap = new HashMap<String, MavenSourceInfo>();
    private final Map<String, JarFile> sourceFileMap = new HashMap<String, JarFile>();
    private File mavenDownloads;
    private String mavenRepo;

    public MavenSourceArtifactSourceSource() {
        String mavenDownloadsEnv = getConfig("REVOC_MAVEN_DOWNLOAD");
        String mavenRepo = System.getenv("REVOC_MAVEN_REPO");

        if(mavenDownloadsEnv != null && mavenRepo != null) {
            File mavenDir = new File(mavenDownloadsEnv);
            mavenDir.mkdirs();
            this.mavenDownloads = mavenDir;
            if(!mavenRepo.endsWith("/")) {
                mavenRepo +="/";
            }
            this.mavenRepo = mavenRepo;

        }


    }

    private String getConfig(String property) {
        String env = System.getenv(property);
        if(env == null) {
            return System.getProperty(property);
        } else {
            return env;
        }

    }

    public String[] getSource(String className, ClassLoader classLoader) {

        URL resource = classLoader.getResource(className + ".class");

        String filePath = getFilePath(resource);

        if (filePath == null) {
            return null;
        }





        try {


            MavenSourceInfo info;

            synchronized (mavenInfoMap) {

                if (!mavenInfoMap.containsKey(filePath)) {
                    mavenInfoMap.put(filePath, parseInfo(filePath, resource));
                }
                info = mavenInfoMap.get(filePath);
            }


            JarFile sourceFile;
            synchronized (sourceFileMap) {
                if(!sourceFileMap.containsKey(filePath)) {
                    sourceFileMap.put(filePath, getSourceFile(filePath, info));
                }
                sourceFile = sourceFileMap.get(filePath);
            }


            if(sourceFile == null) {
                return null;
            }
            String sourcePath = className.replace('.', '/') + ".java";
            ZipEntry entry = sourceFile.getEntry(sourcePath);
            if (entry == null) {
                return null;
            }
            InputStream inputStream = sourceFile.getInputStream(entry);
            List<String> lines = IOUtils.readLines(inputStream);
            return lines.toArray(new String[lines.size()]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private JarFile getSourceFile(String filePath, MavenSourceInfo info) throws IOException {
        if(!filePath.endsWith(".jar")) {
            return null;
        }
        File sourceJar;
        if(info == null) {
            sourceJar = new File(filePath.substring(0, filePath.lastIndexOf(".jar")) +"-sources.jar");
        } else {
            File m2Repo = new File(System.getProperty("user.home"), ".m2/repository");
            sourceJar = new File(m2Repo, getSourceFileMavenPath(info));
            if (!sourceJar.exists()) {
                sourceJar = new File(new File(filePath).getParentFile(), getSourceArtifactName(info));
            }
        }
        if (!sourceJar.exists()) {
            if(mavenDownloads != null && info != null) {
                File downLoad = downloadMavenSource(info);
                if(downLoad != null) {
                    sourceJar = downLoad;
                }

            }
            if(!sourceJar.exists()) {
                return null;
            }
        }


        return new JarFile(sourceJar);
    }

    private File downloadMavenSource(MavenSourceInfo info) {
        File downloadFile = new File(mavenDownloads, getSourceFileMavenPath(info));
        if(info.getVersion().contains("-SNAPSHOT")) {
            downloadFile.delete();
        }
        if(downloadFile.exists()) {
            return downloadFile;
        }
        downloadFile.getParentFile().mkdirs();
        File tempFile = new File(downloadFile.getParentFile(), "_tmp" + downloadFile.getName());

        try {
            URL remoteSourceURL = new URL(this.mavenRepo + getSourceFileDownloadMavenPath(info));

            InputStream stream = remoteSourceURL.openStream();
            FileOutputStream output = new FileOutputStream(tempFile);
            IOUtils.copy(stream, output);
            output.close();
            tempFile.renameTo(downloadFile);
            return downloadFile;
        } catch (MalformedURLException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String getSourceFileMavenPath(MavenSourceInfo info) {
        StringBuilder sb = new StringBuilder();
        appendArtifactDirectory(info, sb);
        sb.append(getSourceArtifactName(info));
        return sb.toString();
    }

    private String getSourceFileDownloadMavenPath(MavenSourceInfo info) {

        StringBuilder sb = new StringBuilder();
        appendArtifactDirectory(info, sb);
        if(info.getVersion().contains("SNAPSHOT")) {
            sb.append(resolveSnapshotFileName(info, sb.toString()));
        } else {
            sb.append(getSourceArtifactName(info));
        }
        return sb.toString();
    }

    private void appendArtifactDirectory(MavenSourceInfo info, StringBuilder sb) {
        sb.append(info.getGroupId().replace('.', '/')).append("/");
        sb.append(info.getArtifactId()).append("/");
        sb.append(info.getVersion()).append("/");
    }

    private String resolveSnapshotFileName(MavenSourceInfo info, String artifactUrl) {

              try {
                  URL url = new URL(this.mavenRepo + artifactUrl + "maven-metadata.xml");

                  final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

                  HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                  urlConnection.addRequestProperty("Cache-Control", "max-age=0");
                  urlConnection.addRequestProperty("User-Agent", "Apache-Maven/3.0");
                  InputStream inputStream = urlConnection.getInputStream();
                  final Document doc = builder.parse(inputStream);
                  inputStream.close();


                  Element versioning = (Element) doc.getDocumentElement().getElementsByTagName("versioning").item(0);
                  Element snapshotVersions = (Element) versioning.getElementsByTagName("snapshotVersions").item(0);
                  NodeList snapshotVersion = snapshotVersions.getElementsByTagName("snapshotVersion");
                  for(int i = 0; i < snapshotVersion.getLength(); i++) {
                      final Element versioningElement = (Element) snapshotVersion.item(i);

                      String value= getText((Element) versioningElement.getElementsByTagName("value").item(0));

                      return info.getArtifactId() + "-" + value + "-sources.jar";


                  }

                  return null;

              } catch (ParserConfigurationException e) {
                  throw new RuntimeException(e);
              } catch (SAXException e) {
                  throw new RuntimeException(e);
              } catch (IOException e) {
                  throw new RuntimeException(e);
              }
    }

    private String getText(Element versionNode) {
        StringBuilder text = new StringBuilder();

        for (int t = 0; t < versionNode.getChildNodes().getLength(); t++) {
            final Node node = versionNode.getChildNodes().item(t);
            if (node instanceof Text) {
                text.append(((Text) node).getData());
            }

        }
        return text.toString();
    }

    private String getSourceArtifactName(MavenSourceInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(info.getArtifactId()).append("-").append(info.getVersion()).append("-sources.jar");
        return sb.toString();
    }

    private String getFilePath(URL resource) {
        if (resource == null) {
            return null;
        }
        if (!resource.getProtocol().equals("jar")) {
            return null;
        }
        String filePath = resource.getPath();
        if (!filePath.startsWith("file:") || !filePath.contains("!")) {
            return null;
        }
        filePath = filePath.substring("file:".length());
        return URLDecoder.decode(filePath.substring(0, filePath.indexOf("!")));
    }

    private MavenSourceInfo parseInfo(String filePath, URL resource) {


        File file = new File(filePath);

        JarFile jarFile = null;
        boolean isNewFile = false;
        try {
            URLConnection urlConnection = resource.openConnection();
            if (urlConnection instanceof JarURLConnection) {
                jarFile = ((JarURLConnection) urlConnection).getJarFile();
            } else {
                jarFile = new JarFile(file);
                isNewFile = true;
            }
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String prefix = "META-INF/maven/";
                String propSuffix = "/pom.properties";

                if (entry.getName().startsWith(prefix) && entry.getName().endsWith(propSuffix)) {
                    Properties props = new Properties();
                    InputStream inputStream = jarFile.getInputStream(entry);
                    props.load(inputStream);
                    inputStream.close();
                    String groupId = props.getProperty("groupId");
                    String artifactId = props.getProperty("artifactId");
                    String version = props.getProperty("version");

                    if (file.getName().startsWith(artifactId + "-" + version)) {
                        return new MavenSourceInfo(groupId, artifactId, version);
                    }

                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (isNewFile) {
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }


    private class MavenSourceInfo {

        private final String groupId;
        private final String artifactId;
        private final String version;
        private JarFile sourceFile;

        public MavenSourceInfo(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getVersion() {
            return version;
        }

        public void setSourceFile(JarFile sourceFile) {
            this.sourceFile = sourceFile;
        }

        public JarFile getSourceFile() {
            return sourceFile;
        }
    }


    public static void main(String[] args) throws IOException {
        URL url = new URL("http://nexus.kantega.lan/content/repositories/snapshots/no/kantega/davexchange/1.6.16-SNAPSHOT/maven-metadata.xml");
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("Cache-Control", "max-age=0");
        urlConnection.addRequestProperty("User-Agent", "Apache-Maven/3.0");
        InputStream inputStream = urlConnection.getInputStream();

        String content = IOUtils.toString(inputStream);

        System.out.println(content);
    }
}
