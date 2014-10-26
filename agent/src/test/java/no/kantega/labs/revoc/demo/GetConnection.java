package no.kantega.labs.revoc.demo;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 */
public class GetConnection {

    private static boolean debugConnections = false;

    public GetConnection() {
        debugConnections = false;
    }

    public String getConnection() {

        Connection c = null;

        try {


        } finally {
            try {
                if(c != null) {
                    c.close();
                }
            } catch (SQLException e) {

            }
        }

        return "";
    }

    public static void main(String[] args) {
        new GetConnection().getConnection();
    }
}
