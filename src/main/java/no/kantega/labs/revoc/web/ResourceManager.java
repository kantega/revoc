package no.kantega.labs.revoc.web;

import no.kantega.labs.revoc.report.HtmlReport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 *
 */
public class ResourceManager {

    private File resources = null;

    public ResourceManager() {
        String src = System.getProperty("revoc.dev");
        if(src != null) {
            File srcFile = new File(src);
            if(srcFile.exists()) {
                File resources = new File(src, "src/main/resources/no/kantega/labs/revoc/report");
                if(resources.exists()) {
                    this.resources = resources;
                }
            }
        }
    }

    public  InputStream getResourceStream(String resourcePath) {
        if(resources != null) {
            File source = new File(resources, resourcePath);
            if(source.exists()) {
                try {
                    return new FileInputStream(source);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return HtmlReport.class.getResourceAsStream(resourcePath);
    }
}
