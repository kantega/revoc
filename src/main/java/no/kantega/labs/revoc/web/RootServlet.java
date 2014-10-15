package no.kantega.labs.revoc.web;

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class RootServlet extends HttpServlet{
    private final ResourceManager resourceManager;

    public RootServlet(ResourceManager resourceManager) {

        this.resourceManager = resourceManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        IOUtils.copy(resourceManager.getResourceStream("revoc.html"), response.getOutputStream());
        return;

    }
}
