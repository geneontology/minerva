package org.geneontology.minerva.server.handler;

import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.repository.RepositoryException;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * SPARQL query endpoint
 * SPARQL query result will be serialized by either
 * SPARQLResultsMessageBodyWriter or SPARQLGraphMessageBodyWriter
 */
@Path("/sparql")
public class SPARQLHandler {

    private final BlazegraphMolecularModelManager m3;
    private final int timeout;

    public SPARQLHandler(BlazegraphMolecularModelManager m3, int timeout) {
        this.m3 = m3;
        this.timeout = timeout;
    }

    @GET
    public QueryResult sparqlQueryGet(@QueryParam("query") String queryText) throws QueryEvaluationException, MalformedQueryException, RepositoryException {
        return m3.executeSPARQLQuery(queryText, timeout);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public QueryResult sparqlQueryPostForm(@FormParam("query") String queryText) throws QueryEvaluationException, MalformedQueryException, RepositoryException {
        return m3.executeSPARQLQuery(queryText, timeout);
    }

    @POST
    @Consumes("application/sparql-query")
    public QueryResult sparqlQueryPostQuery(String query) throws QueryEvaluationException, MalformedQueryException, RepositoryException {
        return m3.executeSPARQLQuery(query, timeout);
    }

}
