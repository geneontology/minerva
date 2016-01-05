package org.geneontology.minerva.server.handler;

import com.google.gson.annotations.SerializedName;

public abstract class MinervaResponse<DATA> {

	@SerializedName("packet-id")
	final String packetId; // generated or pass-through
	final String uid; // pass-through
	
	@SerializedName("is-reasoned")
	boolean isReasoned = false;
	
	/*
	 * pass-through; model:
	 * "query", "action" //, "location"
	 */
	final String intention;
	
	public static final String SIGNAL_MERGE = "merge";
	public static final String SIGNAL_REBUILD = "rebuild";
	public static final String SIGNAL_META = "meta";
	/*
	 * "merge", "rebuild", "meta" //, "location"?
	 */
	String signal;
	
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
	/*
	 * Now degraded to just a String, not an Object.
	 */
	//Map<String, Object> commentary = null;
	String commentary;
	
	DATA data;
	
	/**
	 * @param uid
	 * @param intention
	 * @param packetId
	 */
	public MinervaResponse(String uid, String intention, String packetId) {
		this.uid = uid;
		this.intention = intention;
		this.packetId = packetId;
	}

	/**
	 * @param isReasoned the isReasoned to set
	 */
	public void setReasoned(boolean isReasoned) {
		this.isReasoned = isReasoned;
	}
}
