package org.geneontology.minerva.server.handler;

import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.resultio.QueryResultIO;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriterRegistry;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces({
        MediaType.APPLICATION_JSON,
        "application/ld+json",
        "application/rdf+xml",
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML,
        "text/turtle",
        "application/x-turtle",
        "text/n3",
        "text/rdf+n3",
        "application/rdf+n3",
        MediaType.TEXT_PLAIN
})
public class SPARQLGraphMessageBodyWriter implements MessageBodyWriter<GraphQueryResult> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public long getSize(GraphQueryResult result, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(GraphQueryResult result, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        RDFFormat format = RDFWriterRegistry.getInstance().getFileFormatForMIMEType(mediaType.toString(), RDFFormat.TURTLE);
        try {
            QueryResultIO.write(result, format, entityStream);
            entityStream.flush();
            result.close();
        } catch (RDFHandlerException | QueryEvaluationException e) {
            throw new WebApplicationException(e);
        }

    }
}
