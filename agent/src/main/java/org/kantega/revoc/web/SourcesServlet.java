package org.kantega.revoc.web;

import org.kantega.revoc.registry.Registry;
import org.kantega.revoc.source.CompondSourceSource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class SourcesServlet extends HttpServlet {
    private final CompondSourceSource sourceSource;
    private final String[] packages;

    public SourcesServlet(CompondSourceSource sourceSource, String[] packages) {
        this.sourceSource = sourceSource;
        this.packages = packages;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String className = request.getRequestURI().substring("/sources/".length());
        if(!isPackageMatch(className)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
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
    }

    private boolean isPackageMatch(String className) {
        for (String p : packages) {
            if(className.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
