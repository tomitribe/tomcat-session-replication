package com.tomitribe.support.tomcat.session.replication;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;

public class SessionReplicationServlet extends HttpServlet {
    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        final HttpSession session = req.getSession();
        final Object data = session.getAttribute("data");
        if (data != null) {
            ((Data) data).incrementCounter();
            session.setAttribute("data", data);
        } else {
            session.setAttribute("data", new Data());
        } 

        resp.getWriter().write("<html>");
        resp.getWriter().write("<body>");
        resp.getWriter().write("<br>host: " + InetAddress.getLocalHost().getHostName());
        resp.getWriter().write("<br>session id: " + session.getId());
        resp.getWriter().write("<br>data: " + session.getAttribute("data"));
        resp.getWriter().write("</body>");
        resp.getWriter().write("</html>");
    }

    public static class Data implements Serializable {
        private int counter = 0;

        public void incrementCounter() {
            counter ++;
        }

        @Override
        public String toString() {
            return "Data{counter=" + counter + "}";
        }
    }
}
