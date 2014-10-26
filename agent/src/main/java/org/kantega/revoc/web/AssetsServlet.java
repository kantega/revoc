package org.kantega.revoc.web;

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class AssetsServlet extends HttpServlet {
    private final ResourceManager resourceManager;

    public AssetsServlet(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String asset = req.getRequestURI().substring(req.getServletPath().length()+1);
        InputStream resourceStream = resourceManager.getResourceStream(asset);
        if(resourceStream != null) {
            resp.setContentType(req.getServletContext().getMimeType(asset));
            IOUtils.copy(resourceStream, resp.getOutputStream());
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
