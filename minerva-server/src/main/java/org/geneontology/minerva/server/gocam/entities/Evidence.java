package org.geneontology.minerva.server.gocam.entities;

import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.util.AnnotationShorthand;

public class Evidence extends Entity {
	private Entity evidence;
	private String reference;
	private String with;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private String date;

	public Evidence(String uuid, String id, String label) {
		super(uuid, id, label, EntityType.EVIDENCE);

		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
	}		
	
	public Evidence(String uuid, JsonOwlObject type) {
		this(uuid, type.id, type.label);
	}
	
	public Evidence(String uuid, JsonOwlObject type, JsonAnnotation[] annotations) {
		this(uuid, type.id, type.label);
		this.addAnnotations(annotations);
	}
	
	public boolean addContributor(Contributor contributor) {
		return contributors.add(contributor);
	}

	public boolean addGroup(Group group) {
		return groups.add(group);
	}
	
	public void addAnnotations(JsonAnnotation[] annotations) {
		for (JsonAnnotation annotation : annotations) {
			if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
				addContributor(new Contributor(annotation.value));
			}

			if (AnnotationShorthand.providedBy.name().equals(annotation.key)) {
				addGroup(new Group(annotation.value));
			}

			if (AnnotationShorthand.modelstate.name().equals(annotation.key)) {
				setDate(annotation.value);
			}
			
			if (AnnotationShorthand.source.name().equals(annotation.key)) {
				setReference(annotation.value);
			}
			
			if (AnnotationShorthand.with.name().equals(annotation.key)) {
				setWith(annotation.value);
			}
		}
	}
	
	//Getters and Setters
	
	public Entity getEvidence() {
		return evidence;
	}

	public void setEvidence(Entity evidence) {
		this.evidence = evidence;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public String getWith() {
		return with;
	}

	public void setWith(String with) {
		this.with = with;
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

}