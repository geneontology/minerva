package org.geneontology.minerva.json;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geneontology.minerva.curie.CurieHandler;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.resultio.QueryResultIO;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.semanticweb.owlapi.model.IRI;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class SPARQLResultJSONRenderer {

    private final CurieHandler curieHandler;

    public SPARQLResultJSONRenderer(CurieHandler curieHandler) {
        this.curieHandler = curieHandler;
    }

    public JsonObject renderResults(TupleQueryResult sparqlResults) throws QueryEvaluationException, IOException, TupleQueryResultHandlerException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        QueryResultIO.write(sparqlResults, TupleQueryResultFormat.JSON, stream);
        String json = stream.toString("UTF-8");
        stream.close();
        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
        JsonArray bindings = jsonObject.get("results").getAsJsonObject().getAsJsonArray("bindings");
        for (JsonElement bindingSet : bindings) {
            for (Map.Entry<String, JsonElement> binding : bindingSet.getAsJsonObject().entrySet()) {
                JsonObject value = binding.getValue().getAsJsonObject();
                if (value.getAsJsonPrimitive("type").getAsString().equals("uri")) {
                    String uri = value.getAsJsonPrimitive("value").getAsString();
                    String curie = curieHandler.getCuri(IRI.create(uri));
                    value.addProperty("value", curie);
                }
            }
        }
        return jsonObject;
    }

    public JsonObject renderGraph(GraphQueryResult result) throws QueryEvaluationException, IOException, RDFHandlerException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        QueryResultIO.write(result, RDFFormat.RDFJSON, stream);
        String json = stream.toString("UTF-8");
        stream.close();
        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
        JsonObject newJsonObject = new JsonObject();
        for (Map.Entry<String, JsonElement> subject : jsonObject.entrySet()) {
            String subjectCurie = curieHandler.getCuri(IRI.create(subject.getKey()));
            JsonObject predicatesAndValues = subject.getValue().getAsJsonObject();
            JsonObject newPredicatesAndValues = new JsonObject();
            for (Map.Entry<String, JsonElement> predicate : predicatesAndValues.entrySet()) {
                String predicateCurie = curieHandler.getCuri(IRI.create(predicate.getKey()));
                for (JsonElement value : predicate.getValue().getAsJsonArray()) {
                    JsonObject valueObj = value.getAsJsonObject();
                    if (valueObj.getAsJsonPrimitive("type").getAsString().equals("uri")) {
                        String uri = valueObj.getAsJsonPrimitive("value").getAsString();
                        String curie = curieHandler.getCuri(IRI.create(uri));
                        valueObj.addProperty("value", curie);
                    }
                }
                newPredicatesAndValues.add(predicateCurie, predicate.getValue());
                newJsonObject.add(subjectCurie, newPredicatesAndValues);
            }
        }
        return newJsonObject;

    }

}
