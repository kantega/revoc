package org.kantega.revoc.web;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.AbstractLogger;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.kantega.revoc.logging.JettyRevocLogger;
import org.kantega.revoc.source.CompondSourceSource;
import org.kantega.revoc.source.DirectorySourceSource;
import org.kantega.revoc.source.MavenProjectSourceSource;
import org.kantega.revoc.source.MavenSourceArtifactSourceSource;

import java.awt.*;
import java.io.File;
import java.net.NetworkInterface;
import java.net.URI;

import static java.util.Collections.list;


/**
 *
 */
public class JettyStarter {


    public void start(int port, String[] packages) throws Exception {

        Log.setLog(new JettyRevocLogger());
        Server server = new Server(port);


        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
        ctx.addServlet(RevocWebSocketServlet.class, "/ws").setInitOrder(1);

        ctx.setInitParameter(WebSocketServletFactory.class.getName(), WebSocketServletFactory.class.getName());
        ctx.setInitParameter(WebSocketServerFactory.class.getName(), WebSocketServerFactory.class.getName());

        ctx.addServlet(new ServletHolder(new SourcesServlet(new CompondSourceSource(
                new DirectorySourceSource(),
                new MavenProjectSourceSource(),
                new MavenSourceArtifactSourceSource()), packages)), "/sources/*");

        ctx.addServlet(new ServletHolder(new JsonServlet()), "/json");
        ResourceManager resourceManager = new ResourceManager();
        ctx.addServlet(new ServletHolder(new ResourceServlet(resourceManager, "revoc.html")), "");
        ctx.addServlet(new ServletHolder(new AssetsServlet(resourceManager)), "/assets/*");
        ctx.addServlet(new ServletHolder(new WebjarsServlet()), "/webjars/*");

        server.setHandler(ctx);

        server.setStopAtShutdown(true);
        server.start();

    }

    public void startSetup(File agentFile, int port) throws Exception {
        Log.setLog(new NullLogger());
        Server server = new Server(port);

        ServletContextHandler ctx = new ServletContextHandler();

        ResourceManager resourceManager = new ResourceManager();
        ctx.addServlet(new ServletHolder(new ResourceServlet(resourceManager, "setup.html")), "");
        ctx.addServlet(new ServletHolder(new AssetsServlet(resourceManager)), "/assets/*");
        ctx.addServlet(new ServletHolder(new WebjarsServlet()), "/webjars/*");
        ctx.addServlet(new ServletHolder(new AgentFileServlet(agentFile)), "/agent-file");
        ctx.addServlet(new ServletHolder(new MavenRepoServlet()), "/maven-repo");
        ctx.addServlet(new ServletHolder(new FinishedServlet(server)), "/finished");

        server.setHandler(ctx);
        server.start();

        ServerConnector connector = (ServerConnector) server.getConnectors()[0];

        URI uri = new URI("http://" + getAddress() +":" + connector.getLocalPort() +"/");

        System.out.println();
        System.out.println("Revoc is waiting for you here:");
        System.out.println();
        System.out.println("    " + uri);
        System.out.println();
        if(!GraphicsEnvironment.isHeadless()) {
            Desktop desktop = Desktop.getDesktop();
            if(desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(uri);
            }
        }
        server.join();
    }

    private String getAddress() throws Exception {
        for(NetworkInterface ni : list(NetworkInterface.getNetworkInterfaces())) {
            if(!ni.isLoopback()) {
                return ni.getInetAddresses().nextElement().getHostName();
            }
        }

        for(NetworkInterface ni : list(NetworkInterface.getNetworkInterfaces())) {
            if(ni.isLoopback()) {
                return ni.getInetAddresses().nextElement().getHostName();
            }
        }
        return "127.0.0.1";
    }

    private class NullLogger extends AbstractLogger {
        @Override
        protected Logger newLogger(String fullname) {
            return new NullLogger();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public void warn(String msg, Object... args) {

        }

        @Override
        public void warn(Throwable thrown) {

        }

        @Override
        public void warn(String msg, Throwable thrown) {

        }

        @Override
        public void info(String msg, Object... args) {

        }

        @Override
        public void info(Throwable thrown) {

        }

        @Override
        public void info(String msg, Throwable thrown) {

        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void setDebugEnabled(boolean enabled) {

        }

        @Override
        public void debug(String msg, Object... args) {

        }

        @Override
        public void debug(String msg, long arg) {

        }

        @Override
        public void debug(Throwable thrown) {

        }

        @Override
        public void debug(String msg, Throwable thrown) {

        }

        @Override
        public void ignore(Throwable ignored) {

        }

    }
}
