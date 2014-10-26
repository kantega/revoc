package org.kantega.revoc.web;

import org.kantega.revoc.registry.Registry;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.BitSet;

/**
 *
 */
@WebSocket
public class RevocSocket implements Registry.ChangeListener {
    private Session session;

    public RevocSocket() {

    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        System.out.printf("Connection closed: %d - %s%n", statusCode, reason);
        Registry.removeChangeListener(this);
        this.session = null;

    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.printf("Got connect: %s%n", session);
        this.session = session;
        sendMessages(null);
        Registry.addChangeListener(this);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        System.out.printf("Got msg: %s%n", msg);
    }

    @Override
    public void onChange(BitSet bs) {
        sendMessages(bs);
    }

    private void sendMessages(BitSet changed) {
        StringWriter sw = new StringWriter();
        new JsonHandler().writeJson(Registry.getCoverageData(), new PrintWriter(sw), changed);
        String msg = sw.toString();

        try {
            session.getRemote().sendString(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
