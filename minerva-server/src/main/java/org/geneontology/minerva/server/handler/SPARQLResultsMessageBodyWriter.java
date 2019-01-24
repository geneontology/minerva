package org.geneontology.minerva.server.handler;

import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.resultio.QueryResultIO;
import org.openrdf.query.resultio.TupleQueryResultFormat;

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
        "application/sparql-results+json",
        MediaType.APPLICATION_JSON,
        "application/sparql-results+xml",
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML,
        "text/csv",
        "text/tab-separated-values",
        MediaType.TEXT_PLAIN
})
public class SPARQLResultsMessageBodyWriter implements MessageBodyWriter<TupleQueryResult> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public long getSize(TupleQueryResult result, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(TupleQueryResult result, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        TupleQueryResultFormat format = QueryResultIO.getWriterFormatForMIMEType(mediaType.toString(), TupleQueryResultFormat.JSON);
        try {
            QueryResultIO.write(result, format, entityStream);
            entityStream.flush();
            result.close();
        } catch (TupleQueryResultHandlerException | QueryEvaluationException e) {
            throw new WebApplicationException(e);
        }
    }

}
