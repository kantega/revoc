package no.kantega.labs.revoc.agent;

import no.kantega.labs.revoc.source.CompondSourceSource;
import no.kantega.labs.revoc.source.DirectorySourceSource;
import no.kantega.labs.revoc.source.MavenProjectSourceSource;
import no.kantega.labs.revoc.source.MavenSourceArtifactSourceSource;
import no.kantega.labs.revoc.web.RevocWebSocketServlet;
import no.kantega.labs.revoc.web.WebHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.util.Properties;

import static no.kantega.labs.revoc.agent.Log.err;
import static no.kantega.labs.revoc.agent.Log.log;

/**
 *
 */
public class JettyStarter {


    public void start(Properties props) throws Exception {
        String port = props.getProperty("port");
        if (port != null) {
            log("Using HTTP port " + port);

        } else {
            port = "7070";
        }
        int p = 0;
        try {
            p = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            err("Port is not a number: " + port);
            System.exit(-1);
        }

        Server server = new Server(p);

        HandlerList collection = new HandlerList();

        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/ws");
        ctx.addServlet(RevocWebSocketServlet.class, "/ws");
        collection.addHandler(ctx);

        collection.addHandler(new WebHandler(new CompondSourceSource(
                new DirectorySourceSource(),
                new MavenProjectSourceSource(),
                new MavenSourceArtifactSourceSource())));


        server.setHandler(collection);

        server.start();
    }
}
