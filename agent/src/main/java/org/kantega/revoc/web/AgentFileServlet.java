package org.kantega.revoc.web;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 *
 */
public class AgentFileServlet extends HttpServlet {

    private final File agentFile;

    public AgentFileServlet(File agentFile) {

        this.agentFile = agentFile;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write(agentFile.getAbsolutePath());
    }
}
