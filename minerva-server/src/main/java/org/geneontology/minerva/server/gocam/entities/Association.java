package org.geneontology.minerva.server.gocam.entities;

import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.server.gocam.GOCamTools;
import org.geneontology.minerva.util.AnnotationShorthand;

public class Association extends Entity {
	private String sourceId;
	private String targetId;
	private Set<Evidence> evidences;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private String date;

	// if full object is specified otherwise these will be null
	private Term source;
	private Term target;
	
	public Association(String sourceId, String targetId, String id, String label) {
		super(null, id, label, EntityType.TERM_ASSOCIATION);
		this.sourceId = sourceId;
		this.targetId = targetId;
		this.evidences = new HashSet<Evidence>();
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
	}
	
	public Association(JsonOwlFact fact) {
		this(fact.subject, fact.object, fact.property, fact.propertyLabel);
	}	
	
	public boolean addContributor(Contributor contributor) {
		return contributors.add(contributor);
	}
	
	public boolean addEvidence(Evidence evidence) {
		return this.evidences.add(evidence);
	}	
	
	private void addEvidence(JsonOwlIndividual[] individuals, String uuid) {
		Evidence evidence = GOCamTools.getEvidenceFromId(individuals, uuid);
		addEvidence(evidence);
	}

	public boolean addGroup(Group group) {
		return groups.add(group);
	}
	
	public void addAnnotations(JsonOwlIndividual[] individuals, JsonAnnotation[] annotations) {
		for (JsonAnnotation annotation : annotations) {
			
			if (AnnotationShorthand.evidence.name().equals(annotation.key)) {
				addEvidence(individuals, annotation.value);
			}
			
			if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
				addContributor(new Contributor(annotation.value));
			}
			
			if (AnnotationShorthand.providedBy.name().equals(annotation.key)) {
				addGroup(new Group(annotation.value));
			}

			if (AnnotationShorthand.modelstate.name().equals(annotation.key)) {
				setDate(annotation.value);
			}			
		}
	}


	//Getters and Setters
	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

	public String getTargetId() {
		return targetId;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public Set<Evidence> getEvidences() {
		return evidences;
	}

	public void setEvidences(Set<Evidence> evidences) {
		this.evidences = evidences;
	}

	public Set<Contributor> getContributors() {
		return contributors;
	}



	public void setContributors(Set<Contributor> contributors) {
		this.contributors = contributors;
	}

	public Set<Group> getGroups() {
		return groups;
	}

	public void setGroups(Set<Group> groups) {
		this.groups = groups;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public Term getSource() {
		return source;
	}

	public void setSource(Term source) {
		this.source = source;
	}

	public Term getTarget() {
		return target;
	}

	public void setTarget(Term target) {
		this.target = target;
	}
		
}
