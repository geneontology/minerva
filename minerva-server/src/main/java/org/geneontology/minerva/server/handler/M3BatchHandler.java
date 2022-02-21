package org.geneontology.minerva.server.handler;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.geneontology.minerva.json.*;
import org.geneontology.minerva.validation.ValidationResultSet;

import javax.ws.rs.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/")
public interface M3BatchHandler {

    public static class M3Request extends MinervaRequest<Operation, Entity, M3Argument> {
        // wrapper to conform to minerva request standard
    }

    public static enum Entity {
        individual,
        edge,
        model,
        meta;
    }

    public static enum Operation {
        // generic operations
        get,

        @SerializedName("export-all")
        exportAll,

        @SerializedName("add-type")
        addType,

        @SerializedName("remove-type")
        removeType,

        add,

        remove,

        @SerializedName("add-annotation")
        addAnnotation,

        @SerializedName("remove-annotation")
        removeAnnotation,

        // model specific operations
        @SerializedName("export")
        exportModel,

        @SerializedName("export-legacy")
        exportModelLegacy,

        @SerializedName("import")
        importModel,

        @SerializedName("store")
        storeModel,

        @SerializedName("reset")
        resetModel,

        @SerializedName("diff")
        diffModel,

        @SerializedName("update-imports")
        updateImports,

        // undo operations for models
        undo, // undo the latest op
        redo, // redo the latest undo
        @SerializedName("get-undo-redo")
        getUndoRedo, // get a list of all currently available undo and redo for a model

        sparql

    }

    public static class M3Argument extends MinervaRequest.MinervaArgument {

        @SerializedName("model-id")
        String modelId;
        String subject;
        String object;
        String predicate;
        String individual;

        @SerializedName("individual-iri")
        String individualIRI;

        @SerializedName("taxon-id")
        String taxonId;

        @SerializedName("import-model")
        String importModel;
        String format;

        @SerializedName("assign-to-variable")
        String assignToVariable;

        JsonOwlObject[] expressions;
        JsonAnnotation[] values;

        String query;
    }

    public static class M3BatchResponse extends MinervaResponse<M3BatchResponse.ResponseData> {

        public static class ResponseData extends JsonModel {

            @SerializedName("inconsistent-p")
            public Boolean inconsistentFlag;

            @SerializedName("modified-p")
            public Boolean modifiedFlag;

            //TODO starting out here with raw result from robot
            @SerializedName("diff-result")
            public String diffResult;

            public Object undo;
            public Object redo;

            @SerializedName("export-model")
            public String exportModel;

            public MetaResponse meta;

            @SerializedName("sparql-result")
            public JsonObject sparqlResult;

            @SerializedName("validation-results")
            public ValidationResultSet validation_results;

        }

        public static class MetaResponse {
            public JsonRelationInfo[] relations;

            @SerializedName("data-properties")
            public JsonRelationInfo[] dataProperties;

            public JsonEvidenceInfo[] evidence;

            @SerializedName("models-meta")
            public Map<String, List<JsonAnnotation>> modelsMeta;

            @SerializedName("models-meta-read-only")
            public Map<String, Map<String, Object>> modelsReadOnly;
        }

        /**
         * @param uid
         * @param intention
         * @param packetId
         */
        public M3BatchResponse(String uid, Set<String> providerGroups, String intention, String packetId) {
            super(uid, providerGroups, intention, packetId);
        }

    }


    /**
     * Process a batch request. The parameters uid and intention are round-tripped for the JSONP.
     *
     * @param uid          user id, JSONP relevant
     * @param intention    JSONP relevant
     * @param packetId     response relevant, may be null
     * @param requests     batch request
     * @param useReasoner
     * @param isPrivileged true, if the access is privileged
     * @return response object, never null
     */
    public M3BatchResponse m3Batch(String uid, Set<String> providerGroups, String intention, String packetId, M3Request[] requests, boolean useReasoner, boolean isPrivileged);

    /**
     * Jersey REST method for POST with three form parameters.
     *
     * @param intention   JSONP relevant
     * @param packetId
     * @param requests    JSON string of the batch request
     * @param useReasoner
     * @return response convertible to JSON(P)
     */
    @Path("m3Batch")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public M3BatchResponse m3BatchPost(
            @FormParam("intention") String intention,
            @FormParam("packet-id") String packetId,
            @FormParam("requests") String requests,
            @FormParam("use-reasoner") String useReasoner);

    /**
     * Jersey REST method for POST with three form parameters with privileged rights.
     *
     * @param uid            user id, JSONP relevant
     * @param providerGroups user groups, JSONP relevant
     * @param intention      JSONP relevant
     * @param packetId
     * @param requests       JSON string of the batch request
     * @param useReasoner
     * @return response convertible to JSON(P)
     */
    @Path("m3BatchPrivileged")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public M3BatchResponse m3BatchPostPrivileged(
            @FormParam("uid") String uid,
            @FormParam("provided-by") Set<String> providerGroups,
            @FormParam("intention") String intention,
            @FormParam("packet-id") String packetId,
            @FormParam("requests") String requests,
            @FormParam("use-reasoner") String useReasoner);


    /**
     * Jersey REST method for GET with three query parameters.
     *
     * @param intention   JSONP relevant
     * @param packetId
     * @param requests    JSON string of the batch request
     * @param useReasoner
     * @return response convertible to JSON(P)
     */
    @Path("m3Batch")
    @GET
    public M3BatchResponse m3BatchGet(
            @QueryParam("intention") String intention,
            @QueryParam("packet-id") String packetId,
            @QueryParam("requests") String requests,
            @QueryParam("use-reasoner") String useReasoner);

    /**
     * Jersey REST method for GET with three query parameters with privileged rights.
     *
     * @param uid            user id, JSONP relevant
     * @param providerGroups user groups, JSONP relevant
     * @param intention      JSONP relevant
     * @param packetId
     * @param requests       JSON string of the batch request
     * @param useReasoner
     * @return response convertible to JSON(P)
     */
    @Path("m3BatchPrivileged")
    @GET
    public M3BatchResponse m3BatchGetPrivileged(
            @QueryParam("uid") String uid,
            @QueryParam("provided-by") Set<String> providerGroups,
            @QueryParam("intention") String intention,
            @QueryParam("packet-id") String packetId,
            @QueryParam("requests") String requests,
            @QueryParam("use-reasoner") String useReasoner);

}
