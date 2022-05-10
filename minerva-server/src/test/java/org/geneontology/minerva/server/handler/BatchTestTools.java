package org.geneontology.minerva.server.handler;

import org.geneontology.minerva.json.*;
import org.geneontology.minerva.json.JsonOwlObject.JsonOwlObjectType;
import org.geneontology.minerva.server.handler.M3BatchHandler.*;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.MetaResponse;
import org.geneontology.minerva.util.AnnotationShorthand;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BatchTestTools {

    static M3Request copyModel(String modelId) {
        M3Request r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.copy;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        return r;
    }

    static M3Request addIndividual(String modelId, String cls, JsonOwlObject... expressions) {
        M3Request r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.add;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(r.arguments, cls);
        if (expressions != null && expressions.length > 0) {
            JsonOwlObject[] temp = new JsonOwlObject[expressions.length + 1];
            temp[0] = r.arguments.expressions[0];
            for (int i = 0; i < expressions.length; i++) {
                temp[i + 1] = expressions[i];
            }
            r.arguments.expressions = temp;
        }

        return r;
    }

    static M3Request removeIndividual(String modelId, String individual) {
        M3Request r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.remove;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.individual = individual;
        return r;
    }

    static M3Request removeIndividualAnnotation(String modelId, String individual, AnnotationShorthand key, String value) {
        M3Request r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.removeAnnotation;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.individual = individual;
        r.arguments.values = singleAnnotation(key, value);
        return r;
    }

    static M3Request addEdge(String modelId, String sub, String pred, String obj) {
        M3Request r = new M3Request();
        r.entity = Entity.edge;
        r.operation = Operation.add;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.subject = sub;
        r.arguments.predicate = pred;
        r.arguments.object = obj;
        return r;
    }

    static M3Request deleteEdge(String modelId, String sub, String pred, String obj) {
        M3Request r = new M3Request();
        r.entity = Entity.edge;
        r.operation = Operation.remove;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.subject = sub;
        r.arguments.predicate = pred;
        r.arguments.object = obj;
        return r;
    }

    static void setExpressionClass(M3Argument arg, String cls) {
        arg.expressions = new JsonOwlObject[1];
        arg.expressions[0] = new JsonOwlObject();
        arg.expressions[0].type = JsonOwlObjectType.Class;
        arg.expressions[0].id = cls;
    }

    static JsonOwlObject createClass(String cls) {
        JsonOwlObject json = new JsonOwlObject();
        json.type = JsonOwlObjectType.Class;
        json.id = cls;
        return json;
    }

    static JsonOwlObject createSvf(String prop, String filler) {
        JsonOwlObject json = new JsonOwlObject();
        json.type = JsonOwlObjectType.SomeValueFrom;
        json.property = new JsonOwlObject();
        json.property.type = JsonOwlObjectType.ObjectProperty;
        json.property.id = prop;
        json.filler = new JsonOwlObject();
        json.filler.type = JsonOwlObjectType.Class;
        json.filler.id = filler;
        return json;
    }

    static void printJson(Object resp) {
        String json = MolecularModelJsonRenderer.renderToJson(resp, true);
        System.out.println("---------");
        System.out.println(json);
        System.out.println("---------");
    }

    static JsonOwlIndividual[] responseIndividuals(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        return response.data.individuals;
    }

    static JsonOwlFact[] responseFacts(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        return response.data.facts;
    }

    static JsonAnnotation[] responseAnnotations(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        return response.data.annotations;
    }

    static String responseId(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        return response.data.modelId;
    }

    static JsonRelationInfo[] responseRelations(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        assertNotNull(response.data.meta);
        return response.data.meta.relations;
    }

    static JsonRelationInfo[] responseDataProperties(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        assertNotNull(response.data.meta);
        return response.data.meta.dataProperties;
    }

    static Boolean responseInconsistent(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        return response.data.inconsistentFlag;
    }

    static Map<String, List<JsonAnnotation>> responseModelsMeta(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        assertNotNull(response.data.meta);
        return response.data.meta.modelsMeta;
    }

    static JsonEvidenceInfo[] responseEvidences(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        assertNotNull(response.data.meta);
        return response.data.meta.evidence;
    }

    static String responseExport(M3BatchResponse response) {
        assertNotNull(response);
        assertNotNull(response.data);
        return response.data.exportModel;
    }

    static String generateBlankModel(JsonOrJsonpBatchHandler handler) {
        // create blank model
        M3Request[] batch = new M3Request[1];
        batch[0] = new M3Request();
        batch[0].entity = Entity.model;
        batch[0].operation = Operation.add;
        M3BatchResponse resp = handler.m3Batch(BatchModelHandlerTest.uid, BatchModelHandlerTest.providedBy, BatchModelHandlerTest.intention, null, batch, false, true);
        assertEquals(resp.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, resp.messageType);
        assertNotNull(resp.packetId);
        String modelId = responseId(resp);
        assertNotNull(modelId);
        return modelId;
    }

    static MetaResponse getMeta(JsonOrJsonpBatchHandler handler) {
        M3Request[] batch = new M3Request[1];
        batch[0] = new M3Request();
        batch[0].entity = Entity.meta;
        batch[0].operation = Operation.get;
        M3BatchResponse resp = handler.m3Batch(BatchModelHandlerTest.uid, BatchModelHandlerTest.providedBy, BatchModelHandlerTest.intention, null, batch, false, true);
        assertEquals(resp.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, resp.messageType);
        assertNotNull(resp.packetId);
        assertNotNull(resp.data);
        assertNotNull(resp.data.meta);
        return resp.data.meta;
    }

    static M3BatchResponse getModel(JsonOrJsonpBatchHandler handler, String modelId, boolean useReasoner) {
        M3Request[] batch = new M3Request[1];
        batch[0] = new M3Request();
        batch[0].entity = Entity.model;
        batch[0].operation = Operation.get;
        batch[0].arguments = new M3Argument();
        batch[0].arguments.modelId = modelId;
        M3BatchResponse resp = handler.m3Batch(BatchModelHandlerTest.uid, BatchModelHandlerTest.providedBy, BatchModelHandlerTest.intention, null, batch, useReasoner, true);
        assertEquals(resp.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, resp.messageType);
        assertNotNull(resp.packetId);
        return resp;
    }

    static JsonAnnotation[] singleAnnotation(AnnotationShorthand sh, String value) {
        return new JsonAnnotation[]{JsonTools.create(sh, value, null)};
    }

}
