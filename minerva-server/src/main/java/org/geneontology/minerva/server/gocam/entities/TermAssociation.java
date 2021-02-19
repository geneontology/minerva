package org.geneontology.minerva.server.gocam.entities;

public class TermAssociation {
	private String sourceId;
	private String targetId;
	private String relationId;

	// if full object is specified otherwise these will be null
	private Term source;
	private Term target;
	private Relation relation;	
}
