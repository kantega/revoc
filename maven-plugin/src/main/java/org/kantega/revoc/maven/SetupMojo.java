package org.kantega.revoc.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.Console;
import java.io.File;

/**
 *
 */
@Mojo(name = "setup", requiresDependencyResolution = ResolutionScope.COMPILE)
public class SetupMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    MavenProject project;

    @Parameter(defaultValue = "${plugin}")
    PluginDescriptor plugin;

    @Parameter
    int port = 7070;



    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File agentFile = getAgentFile();

        System.out.println();
        String portString = System.console().readLine("Which port should the Revoc web server start on? [%s]: ", port);
        if(portString != null  &&  !portString.isEmpty()) {
            port = Integer.parseInt(portString);
        }

        String packages = System.console().readLine("Which packages do you want to instrument? (com.example): ", port);
        if(portString != null  &&  !portString.isEmpty()) {
            port = Integer.parseInt(portString);
        }

        String agentLine = "-javaagent:" + agentFile + "=port=" + port + ",packages=" + packages;

        System.out.println();
        System.out.println("Startup argument to enable Revoc instrumentation on port " + port + " of packages " + packages);
        System.out.println();
        System.out.println(agentLine);
        System.out.println();
        System.out.println("Using Maven?");
        System.out.println();
        System.out.println("export MAVEN_OPTS+=" + agentLine);
        System.out.println();
        System.out.println("Or Tomcat?");
        System.out.println();
        System.out.println("export CATALINA_OPTS+=" + agentLine);
        System.out.println();
        System.exit(0);
    }

    private File getAgentFile() {
        return plugin.getArtifacts().stream()
                .filter((artifact) -> artifact.getArtifactId().equals("revoc-agent")).findFirst().get().getFile();
    }
}
