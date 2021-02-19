package org.geneontology.minerva.server.gocam.entities;

import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.util.AnnotationShorthand;

public class Term extends Entity {
	private String aspect;
	private boolean isRootNode;
	private boolean isExtension;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private String date;

	public Term(String uuid, String id, String label) {
		super(uuid, id, label, EntityType.TERM);
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
	}

	public Term(String uuid, JsonOwlObject type) {
		this(uuid, type.id, type.label);
	}

	public Term(String uuid, JsonOwlObject type, JsonAnnotation[] annotations) {
		this(uuid, type.id, type.label);
		this.addAnnotations(annotations);
	}

	public String getAspect() {
		return aspect;
	}

	public boolean addContributor(Contributor contributor) {
		return contributors.add(contributor);
	}

	public boolean addGroup(Group group) {
		return groups.add(group);
	}

	public void setAspect(String aspect) {
		this.aspect = aspect;
	}

	public boolean isRootNode() {
		return isRootNode;
	}

	public void setRootNode(boolean isRootNode) {
		this.isRootNode = isRootNode;
	}

	public boolean isExtension() {
		return isExtension;
	}

	public void setExtension(boolean isExtension) {
		this.isExtension = isExtension;
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
		}
	}
}

