package org.kantega.revoc.maven;

import no.kantega.labs.revoc.main.Main;
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

        Main.setup(agentFile, port);
        System.exit(0);
    }

    private File getAgentFile() {
        return plugin.getArtifacts().stream()
                .filter((artifact) -> artifact.getArtifactId().equals("revoc-agent")).findFirst().get().getFile();
    }
}
