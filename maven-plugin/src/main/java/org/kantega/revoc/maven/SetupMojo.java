package org.kantega.revoc.maven;

import org.kantega.revoc.main.Main;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.kantega.revoc.web.JettyStarter;

import java.io.File;

/**
 *
 */
@Mojo(name = "setup", requiresProject = false)
public class SetupMojo extends AbstractMojo {

    @Parameter(defaultValue = "${plugin}")
    PluginDescriptor plugin;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File agentFile = getAgentFile();

        try {
            new JettyStarter().startSetup(agentFile, 0);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private File getAgentFile() {
        return plugin.getArtifacts().stream()
                .filter((artifact) -> artifact.getArtifactId().equals("revoc-agent")).findFirst().get().getFile();
    }
}
