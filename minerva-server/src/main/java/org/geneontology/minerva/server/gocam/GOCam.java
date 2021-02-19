package org.geneontology.minerva.server.gocam;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.server.gocam.data.CausalRelationType;
import org.geneontology.minerva.server.gocam.data.RelationType;
import org.geneontology.minerva.server.gocam.entities.Activity;
import org.geneontology.minerva.server.gocam.entities.ActivityAssociation;
import org.geneontology.minerva.server.gocam.entities.Contributor;
import org.geneontology.minerva.server.gocam.entities.Evidence;
import org.geneontology.minerva.server.gocam.entities.Group;
import org.geneontology.minerva.server.gocam.entities.Taxon;
import org.geneontology.minerva.server.gocam.entities.Term;
import org.geneontology.minerva.util.AnnotationShorthand;

public class GOCam {

	private String id;
	private String title;
	private String state;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private Set<Taxon> taxons;
	private Set<Activity> activities;
	private Set<Evidence> evidences;
	private Set<ActivityAssociation> activityAssociations;

	public GOCam(String id) {
		this.id = id;
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
		this.taxons = new HashSet<Taxon>();
		this.activityAssociations = new HashSet<ActivityAssociation>();
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

	public boolean addActivityAssociation(ActivityAssociation activityAssociation) {
		return this.activityAssociations.add(activityAssociation);
	}

	public boolean addActivity(Activity activity) {
		return this.activities.add(activity);
	}

	public boolean addEvidence(Evidence evidence) {
		return this.evidences.add(evidence);
	}

	public void addActivityAssociation(JsonOwlIndividual[] individuals, JsonOwlFact fact) {
		ActivityAssociation activityAssociation = GOCamTools.getActivityAssociationFromFact(individuals, fact);

		for (Evidence evidence : activityAssociation.getEvidences()) {
			addEvidence(evidence);
		}

		addActivityAssociation(activityAssociation);
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

	public void parse(JsonOwlIndividual[] individuals, JsonOwlFact[] facts) {
		//EnumSet<CausalRelationType> causalRelations = EnumSet.allOf(CausalRelationType.class);
		
		for (JsonOwlFact fact : facts) {
			for (Activity activity : activities) {

				if (CausalRelationType.valueOfId(fact.property) != null && fact.subject.equals(activity.getUuid())) {
				
					addActivityAssociation(individuals, fact);
				}
			}
		}
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

	public Set<Evidence> getEvidences() {
		return evidences;
	}

	public void setEvidences(Set<Evidence> evidences) {
		this.evidences = evidences;
	}

	public Set<ActivityAssociation> getActivityAssociations() {
		return activityAssociations;
	}

	public void setActivityAssociations(Set<ActivityAssociation> activityAssociations) {
		this.activityAssociations = activityAssociations;
	}

}
