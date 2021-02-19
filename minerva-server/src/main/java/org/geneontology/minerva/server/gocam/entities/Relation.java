package org.geneontology.minerva.server.gocam.entities;

import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonOwlFact;


public class Relation extends Entity{
	private Set<Evidence> evidences;
	
	public Relation(String uuid, String id, String label) {
		super(uuid, id, label, EntityType.RELATION);
		evidences = new HashSet<Evidence>();
	}

	public Relation(String uuid, JsonOwlFact fact) {
		this(uuid, fact.property, fact.propertyLabel);
	}

	public Set<Evidence> getEvidences() {
		return evidences;
	}

	public void setEvidences(Set<Evidence> evidences) {
		this.evidences = evidences;
	}
	
	public boolean addEvidence(Evidence evidence) {
		return this.evidences.add(evidence);
	}	
}
