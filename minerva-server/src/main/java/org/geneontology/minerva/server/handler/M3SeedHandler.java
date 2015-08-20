package org.geneontology.minerva.server.handler;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import com.google.gson.annotations.SerializedName;

/**
 * Alpha version interface for seeding a model.
 *
 */
@Path("/seed")
public interface M3SeedHandler {

	public static class SeedRequest {
		
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
	
	public static class SeedResponse {
		@SerializedName("packet-id")
		final String packetId; // generated or pass-through
		final String uid; // pass-through
		/*
		 * pass-through; model:
		 * "query", "action" //, "location"
		 */
		final String intention;
		
		public static final String SIGNAL_REBUILD = "rebuild"; // 
		/*
		 * always rebuild after seed!
		 */
		final String signal = SIGNAL_REBUILD;
		
		public static final String MESSAGE_TYPE_SUCCESS = "success";
		public static final String MESSAGE_TYPE_ERROR = "error";
		/*
		 * "error", "success", //"warning"
		 */
		@SerializedName("message-type")
		String messageType;
		/*
		 * "e.g.: server done borked"
		 */
		String message;
		
		String commentary;
		
		public static class SeedResponseData {
			
			public String id;
		}
		
		SeedResponseData data;
		
		/**
		 * @param uid
		 * @param intention
		 * @param packetId
		 */
		public SeedResponse(String uid, String intention, String packetId) {
			this.uid = uid;
			this.intention = intention;
			this.packetId = packetId;
		}
	}
	
	/**
	 * Jersey REST method for POST with three form parameters.
	 * 
	 * @param intention JSONP relevant
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
			@FormParam("request") String requestString);
	
	/**
	 * Jersey REST method for POST with three form parameters with privileged rights.
	 * 
	 * @param uid user id, JSONP relevant
	 * @param intention JSONP relevant
	 * @param packetId
	 * @param requestString seed request
	 * @return response convertible to JSON(P)
	 */
	@Path("fromProcessPrivileged")
	@POST
	@Consumes("application/x-www-form-urlencoded")
	public SeedResponse fromProcessPostPrivileged(
			@FormParam("uid") String uid,
			@FormParam("intention") String intention,
			@FormParam("packet-id") String packetId,
			@FormParam("request") String requestString);
	
	
	/**
	 * Jersey REST method for GET with three query parameters.
	 * 
	 * @param intention JSONP relevant
	 * @param packetId 
	 * @param requestString seed request
	 * @return response convertible to JSON(P)
	 */
	@Path("fromProcess")
	@GET
	public SeedResponse fromProcessGet(
			@QueryParam("intention") String intention,
			@QueryParam("packet-id") String packetId,
			@QueryParam("request") String requestString);
	
	/**
	 * Jersey REST method for GET with three query parameters with privileged rights.
	 * 
	 * @param uid user id, JSONP relevant
	 * @param intention JSONP relevant
	 * @param packetId 
	 * @param requestString seed request
	 * @return response convertible to JSON(P)
	 */
	@Path("fromProcessPrivileged")
	@GET
	public SeedResponse fromProcessGetPrivileged(
			@QueryParam("uid") String uid,
			@QueryParam("intention") String intention,
			@QueryParam("packet-id") String packetId,
			@QueryParam("request") String requestString);
}
