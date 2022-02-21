package org.geneontology.minerva.server.handler;

import com.google.gson.annotations.SerializedName;
import org.geneontology.minerva.server.handler.MinervaRequest.MinervaArgument;

import javax.ws.rs.*;
import java.util.Set;

/**
 * Alpha version interface for seeding a model.
 */
@Path("/seed")
public interface M3SeedHandler {

    public static class SeedRequest extends MinervaRequest<String, String, SeedRequestArgument> {
        // wrapper to conform to minerva request standard
    }

    public static class SeedRequestArgument extends MinervaArgument {

        String process;
        String taxon;

        /*
         * use the label, as this is the used as a restriction in the
         * 'evidence_type_closure' Golr field
         */
        @SerializedName("evidence-restriction")
        String[] evidenceRestriction = new String[]{"experimental evidence"};

        @SerializedName("location-roots")
        String[] locationRoots = new String[]{"CL:0000003", "GO:0005575"}; // native cell, CC

        @SerializedName("ignore-classes")
        String[] ignoreList = new String[]{"GO:0005515"}; // protein binding
    }

    public static class SeedResponse extends MinervaResponse<SeedResponse.SeedResponseData> {

        public static class SeedResponseData {

            public String id;
        }

        /**
         * @param uid
         * @param intention
         * @param packetId
         */
        public SeedResponse(String uid, Set<String> providerGroups, String intention, String packetId) {
            super(uid, providerGroups, intention, packetId);
        }
    }

    /**
     * Jersey REST method for POST with three form parameters.
     *
     * @param intention     JSONP relevant
     * @param packetId
     * @param requestString seed request
     * @return response convertible to JSON(P)
     */
    @Path("fromProcess")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public SeedResponse fromProcessPost(
            @FormParam("intention") String intention,
            @FormParam("packet-id") String packetId,
            @FormParam("requests") String requestString);

    /**
     * Jersey REST method for POST with three form parameters with privileged rights.
     *
     * @param uid            user id, JSONP relevant
     * @param providerGroups user groups, JSONP relevant
     * @param intention      JSONP relevant
     * @param packetId
     * @param requestString  seed request
     * @return response convertible to JSON(P)
     */
    @Path("fromProcessPrivileged")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public SeedResponse fromProcessPostPrivileged(
            @FormParam("uid") String uid,
            @FormParam("provided-by") Set<String> providerGroups,
            @FormParam("intention") String intention,
            @FormParam("packet-id") String packetId,
            @FormParam("requests") String requestString);


    /**
     * Jersey REST method for GET with three query parameters.
     *
     * @param intention     JSONP relevant
     * @param packetId
     * @param requestString seed request
     * @return response convertible to JSON(P)
     */
    @Path("fromProcess")
    @GET
    public SeedResponse fromProcessGet(
            @QueryParam("intention") String intention,
            @QueryParam("packet-id") String packetId,
            @QueryParam("requests") String requestString);

    /**
     * Jersey REST method for GET with three query parameters with privileged rights.
     *
     * @param uid            user id, JSONP relevant
     * @param providerGroups user groups, JSONP relevant
     * @param intention      JSONP relevant
     * @param packetId
     * @param requestString  seed request
     * @return response convertible to JSON(P)
     */
    @Path("fromProcessPrivileged")
    @GET
    public SeedResponse fromProcessGetPrivileged(
            @QueryParam("uid") String uid,
            @QueryParam("provided-by") Set<String> providerGroups,
            @QueryParam("intention") String intention,
            @QueryParam("packet-id") String packetId,
            @QueryParam("requests") String requestString);
}
