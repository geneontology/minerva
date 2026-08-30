package org.geneontology.minerva.cli;

import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.resultio.sparqlxml.SPARQLResultsXMLWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EmbeddedSparqlEndpoint {

    private final Server server;

    public EmbeddedSparqlEndpoint(BigdataSailRepository repository, int port) {
        // Create server with no arguments
        this.server = new Server();

        // Create and add connector explicitly
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        // Use ServletContextHandler, NOT WebAppContext
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // Register our servlet
        ServletHolder holder = new ServletHolder("sparql", new SparqlServlet(repository));
        context.addServlet(holder, "/sparql/*");

        server.setHandler(context);
    }

    public void start() throws Exception {
        server.start();
        System.out.println("SPARQL endpoint started at http://localhost:" +
                ((ServerConnector) server.getConnectors()[0]).getLocalPort() + "/sparql");
    }

    public void stop() throws Exception {
        server.stop();
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public static class SparqlServlet extends HttpServlet {

        private final BigdataSailRepository repository;

        public SparqlServlet(BigdataSailRepository repository) {
            this.repository = repository;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            handleQuery(req, resp);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            handleQuery(req, resp);
        }

        private void handleQuery(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {

            String query = req.getParameter("query");
            if (query == null || query.trim().isEmpty()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'query' parameter");
                return;
            }

            BigdataSailRepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);

                resp.setContentType("application/sparql-results+xml");
                SPARQLResultsXMLWriter writer = new SPARQLResultsXMLWriter(resp.getOutputStream());
                tupleQuery.evaluate(writer);

            } catch (MalformedQueryException e) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed query: " + e.getMessage());
            } catch (Exception e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
