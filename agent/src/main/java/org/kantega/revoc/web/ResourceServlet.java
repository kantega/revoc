package org.kantega.revoc.web;

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class ResourceServlet extends HttpServlet{
    private final ResourceManager resourceManager;
    private final String path;

    public ResourceServlet(ResourceManager resourceManager, String path) {
        this.resourceManager = resourceManager;
        this.path = path;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType(req.getServletContext().getMimeType(path));
        IOUtils.copy(resourceManager.getResourceStream(path), response.getOutputStream());
    }
}
