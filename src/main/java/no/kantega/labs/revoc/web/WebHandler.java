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
import no.kantega.labs.revoc.report.HtmlReport;
import no.kantega.labs.revoc.source.SourceSource;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationListener;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.BitSet;

/**
 *
 */
public class WebHandler extends AbstractHandler {
    private final SourceSource sourceSource;
    private File resources;

    public WebHandler(SourceSource sourceSource) {
        this.sourceSource = sourceSource;
        File resources = new File("src/main/resources/no/kantega/labs/revoc/report");
        if(resources.exists()) {
            this.resources = resources;
        }
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
            }else if (request.getRequestURI() != null && request.getRequestURI().startsWith("/sources/")) {
                String className = request.getRequestURI().substring("/sources/".length());
                ClassLoader loader =  Registry.getClassLoader(Integer.parseInt(request.getParameter("classLoader")));
                String[] source = sourceSource.getSource(className, loader);
                if(source == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                } else {
                    response.setContentType("text/plain");
                    for(String line : source) {
                        response.getWriter().println(line);
                    }
                }
                return;

            }else if ("/json".equals(request.getRequestURI())) {
                response.setContentType("application/json");
                if(request.getParameter("hang") != null) {

                    if(request.getAttribute("newData") == null) {
                        final Continuation continuation = ContinuationSupport.getContinuation(request);


                        if(continuation.isExpired()) {
                            response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT, "Timeout!");
                            return;
                        }

                        continuation.suspend();


                        final Registry.ChangeListener changeListener = new Registry.ChangeListener() {
                            public void onChange(BitSet bs) {
                                continuation.setAttribute("newData", Boolean.TRUE);
                                continuation.resume();
                            }
                        };

                        continuation.addContinuationListener(new ContinuationListener() {
                            public void onComplete(Continuation continuation) {
                                Registry.removeChangeListener(changeListener);
                            }

                            public void onTimeout(Continuation continuation) {
                                Registry.removeChangeListener(changeListener);
                            }
                        });

                        Registry.addChangeListener(changeListener);
                        return;
                    }

                    new JsonHandler().writeJson(Registry.getCoverageData(), response.getWriter());
                    return;


                } else {
                    new JsonHandler().writeJson(Registry.getCoverageData(), response.getWriter());
                }
                return;
            }else if ("/".equals(request.getRequestURI())) {
                response.setContentType("text/html");
                IOUtils.copy(getResourceStream("revoc.html"), response.getOutputStream());
                return;
            }else if ("/profiler.json".equals(request.getRequestURI())) {
                response.setContentType("application/json");
                new JsonHandler().writeCallTreeJson(Registry.getFrames(), response.getWriter());
                return;
            }else if ("/profiler".equals(request.getRequestURI())) {
                response.setContentType("text/html");
                IOUtils.copy(HtmlReport.class.getResourceAsStream("profiler.html"), response.getOutputStream());
                return;
            }else if ("/revoc.css".equals(request.getRequestURI())) {
                response.setContentType("text/css");
                IOUtils.copy(getResourceStream("revoc.css"), response.getOutputStream());
                return;
            }else if ("/revoc.js".equals(request.getRequestURI())) {
                response.setContentType("text/javascript");
                IOUtils.copy(getResourceStream("revoc.js"), response.getOutputStream());
                return;
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

        } finally {
            baseRequest.setHandled(true);
        }

    }

    private InputStream getResourceStream(String resourcePath) {
        if(resources != null) {
            File source = new File(resources, resourcePath);
            if(source.exists()) {
                try {
                    return new FileInputStream(source);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return HtmlReport.class.getResourceAsStream(resourcePath);
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
