package org.geneontology.minerva.server.gocam.entities;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Evidence extends Entity {
	private Entity evidence;
	private String reference;
	private String with;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private Date date;

	public Evidence(String uuid, String id, String label) {
		super(uuid, id, label, EntityType.EVIDENCE);

		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
	}

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

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

}