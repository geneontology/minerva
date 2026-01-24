package org.geneontology.minerva.server.handler;

import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;
import org.eclipse.rdf4j.query.resultio.QueryResultFormat;
import org.eclipse.rdf4j.query.resultio.QueryResultIO;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;

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
import java.util.Optional;

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
        Optional<QueryResultFormat> format = QueryResultIO.getWriterFormatForMIMEType(mediaType.toString());
        try {
            if (format.isPresent() && format.get() instanceof TupleQueryResultFormat) {
                QueryResultIO.writeTuple(result, ((TupleQueryResultFormat)(format.get())), entityStream);
                entityStream.flush();
            } else throw new TupleQueryResultHandlerException("No format available");
            try {
                ((AutoCloseable)result).close();
            } catch (Exception e) {
                throw new QueryEvaluationException(e);
            }
        } catch (TupleQueryResultHandlerException | QueryEvaluationException e) {
            throw new WebApplicationException(e);
        }
    }

}
