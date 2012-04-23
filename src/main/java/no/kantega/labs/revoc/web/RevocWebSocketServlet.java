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

package no.kantega.labs.revoc.web;

import no.kantega.labs.revoc.registry.Registry;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.BitSet;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 *
 */
public class RevocWebSocketServlet extends WebSocketServlet implements Registry.ChangeListener  {

    private final Set<TimeWebSocket> members = new CopyOnWriteArraySet<TimeWebSocket>();

    @Override
    public void init() throws ServletException {
        super.init();
        Registry.addChangeListener(this);
    }


    @Override
    public void destroy() {
        Registry.removeChangeListener(this);
    }

    @Override
    public void onChange(BitSet bs) {
        sendMessages(members, bs);
    }

    private void sendMessages(Set<TimeWebSocket> members, BitSet changed) {
        StringWriter sw = new StringWriter();
        new JsonHandler().writeJson(Registry.getCoverageData(), new PrintWriter(sw), changed);
        String msg = sw.toString();

        for(TimeWebSocket ws : this.members) {
            ws.sendLatestData(msg);
        }
    }

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        return new TimeWebSocket();
    }


    private class TimeWebSocket implements WebSocket.OnTextMessage {
        private Connection connection;

        @Override
        public void onOpen(Connection connection) {
            this.connection = connection;
            members.add(this);
            sendMessages(Collections.singleton(this), null);
        }

        @Override
        public void onMessage(String data) {

        }


        @Override
        public void onClose(int closeCode, String message) {
            members.remove(this);
        }


        public void sendLatestData(String data) {

            try {
                connection.sendMessage(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
