package org.geneontology.minerva.server.gocam;

import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.server.gocam.entities.Activity;
import org.geneontology.minerva.server.gocam.entities.Contributor;
import org.geneontology.minerva.server.gocam.entities.Group;
import org.geneontology.minerva.server.gocam.entities.Taxon;
import org.geneontology.minerva.util.AnnotationShorthand;

public class GOCam {

	private String id;
	private String title;
	private String state;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private Set<Taxon> taxons;
	private Set<Activity> activities;

	public GOCam(String id) {
		this.id = id;
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
		this.taxons = new HashSet<Taxon>();
	}

	public boolean addContributor(Contributor contributor) {
		return contributors.add(contributor);
	}

	public boolean addGroup(Group group) {
		return groups.add(group);
	}

	public boolean addTaxon(Taxon taxon) {
		return taxons.add(taxon);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Set<Taxon> getTaxons() {
		return taxons;
	}

	public void setTaxons(Set<Taxon> taxons) {
		this.taxons = taxons;
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

	public Set<Activity> getActivities() {
		return activities;
	}

	public void setActivities(Set<Activity> activities) {
		this.activities = activities;
	}

	public void addAnnotations(JsonAnnotation[] annotations) {
		for (JsonAnnotation annotation : annotations) {
			if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
				addContributor(new Contributor(annotation.value));
			}

			if (AnnotationShorthand.providedBy.name().equals(annotation.key)) {
				addGroup(new Group(annotation.value));
			}

			if (AnnotationShorthand.title.name().equals(annotation.key)) {
				setTitle(annotation.value);
			}

			if (AnnotationShorthand.modelstate.name().equals(annotation.key)) {
				setState(annotation.value);
			}

			if (AnnotationShorthand.taxon.name().equals(annotation.key)) {
				addTaxon(new Taxon(annotation.value));
			}
		}
	}
}

