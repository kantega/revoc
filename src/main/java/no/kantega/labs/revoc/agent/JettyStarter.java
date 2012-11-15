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

/**
 *
 */
public class JettyStarter {


    public void start(int port, String[] packages) throws Exception {

        Server server = new Server(port);

        HandlerList collection = new HandlerList();

        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/ws");
        ctx.addServlet(RevocWebSocketServlet.class, "/ws");
        collection.addHandler(ctx);

        collection.addHandler(new WebHandler(new CompondSourceSource(
                new DirectorySourceSource(),
                new MavenProjectSourceSource(),
                new MavenSourceArtifactSourceSource()), packages));


        server.setHandler(collection);

        server.start();
    }
}
