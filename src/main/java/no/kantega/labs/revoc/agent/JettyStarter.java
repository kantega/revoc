package no.kantega.labs.revoc.agent;

import no.kantega.labs.revoc.logging.JettyRevocLogger;
import no.kantega.labs.revoc.source.CompondSourceSource;
import no.kantega.labs.revoc.source.DirectorySourceSource;
import no.kantega.labs.revoc.source.MavenProjectSourceSource;
import no.kantega.labs.revoc.source.MavenSourceArtifactSourceSource;
import no.kantega.labs.revoc.web.RevocWebSocketServlet;
import no.kantega.labs.revoc.web.WebHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 *
 */
public class JettyStarter {


    public void start(int port, String[] packages) throws Exception {

        Log.setLog(new JettyRevocLogger());
        Server server = new Server(port);

        HandlerList collection = new HandlerList();

        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/ws");
        ctx.addServlet(RevocWebSocketServlet.class, "/ws").setInitOrder(1);
        ctx.setInitParameter(WebSocketServletFactory.class.getName(), WebSocketServletFactory.class.getName());
        ctx.setInitParameter(WebSocketServerFactory.class.getName(), WebSocketServerFactory.class.getName());
        collection.addHandler(ctx);

        collection.addHandler(new WebHandler(new CompondSourceSource(
                new DirectorySourceSource(),
                new MavenProjectSourceSource(),
                new MavenSourceArtifactSourceSource()), packages));


        server.setHandler(collection);

        server.setStopAtShutdown(true);
        server.start();

    }
}
