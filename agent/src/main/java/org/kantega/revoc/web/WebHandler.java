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

package org.kantega.revoc.web;

import org.kantega.revoc.registry.Registry;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 *
 */
public class WebHandler extends AbstractHandler {
    private final String[] packages;
    private File resources;

    public WebHandler(String[] packages) {
        this.packages = packages;

    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {

        if (baseRequest.isHandled()) {
            return;
        }
        try {
            if (request.getMethod().equals("POST")) {
                if (request.getParameter("stopServer") != null) {
                    new ShutdownThread(getServer()).start();
                    response.sendRedirect(".");
                    return;
                } else if (request.getParameter("resetRegistry") != null) {
                    Registry.resetVisits();
                    response.sendRedirect(".");
                    return;
                }
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

        } finally {
            baseRequest.setHandled(true);
        }

    }



    class ShutdownThread extends Thread {
        private final Server server;

        public ShutdownThread(Server server) {

            this.server = server;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    server.stop();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

}
