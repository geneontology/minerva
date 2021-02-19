package org.geneontology.minerva.server.gocam.entities;

import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.server.gocam.GOCamTools;
import org.geneontology.minerva.server.gocam.data.RelationType;

public class Activity {
	private ActivityType type;
	private String uuid;
	private String title;
	private Term rootTerm;
	private Term gp;
	private Term mf;
	private Term bp;
	private Term cc;
	private Set<Term> terms;
	private Set<Evidence> evidences;
	private Set<TermAssociation> termAssociations;

	public Activity(Term rootTerm) {
		this.type = ActivityType.ACTIVITY_UNIT;
		this.uuid = rootTerm.getUuid();
		this.title = rootTerm.getLabel();

		this.rootTerm = rootTerm;
		this.mf = rootTerm;

		terms = new HashSet<Term>();
		evidences = new HashSet<Evidence>();
		termAssociations = new HashSet<TermAssociation>();

		addTerm(mf);
	}

	public boolean addTerm(Term term) {
		return this.terms.add(term);
	}

	public boolean addEvidence(Evidence evidence) {
		return this.evidences.add(evidence);
	}

	public boolean addTermAssociation(TermAssociation termAssociation) {
		return this.termAssociations.add(termAssociation);
	}

	public void addTermAssociation(JsonOwlIndividual[] individuals, JsonOwlFact fact) {
		TermAssociation termAssociation = GOCamTools.getTermAssociationFromFact(individuals, fact);

		for (Evidence evidence : termAssociation.getEvidences()) {
			addEvidence(evidence);
		}

		addTermAssociation(termAssociation);
	}

	public void parse(JsonOwlIndividual[] individuals, JsonOwlFact[] facts) {
		for (JsonOwlFact fact : facts) {

			if (fact.property.equals(RelationType.ENABLED_BY.id) && fact.subject.equals(mf.getUuid())) {

				Term term = GOCamTools.getTermFromId(individuals, fact.object);

				if (term != null) {
					setGP(term);
					addTerm(term);
				}

				addTermAssociation(individuals, fact);

				parseDFS(individuals, GOCamTools.getFactsBySubject(facts, fact.subject), fact.subject);
			}
		}
	}

	private void parseDFS(JsonOwlIndividual[] individuals, JsonOwlFact[] facts, String subject) {
		for (JsonOwlFact fact : facts) {

			// Do until the next causal edge with an enabled by unfinished
			if (!fact.property.equals(RelationType.ENABLED_BY.id)) {

				Term term = GOCamTools.getTermFromId(individuals, fact.object);

				if (term != null) {
					this.setGP(term);
					addTerm(term);
				}
				addTermAssociation(individuals, fact);

				parseDFS(individuals, GOCamTools.getFactsBySubject(facts, fact.object), fact.object);
			}
		}
	}

	// Getter and Setters
	public ActivityType getType() {
		return type;
	}

	public void setType(ActivityType type) {
		this.type = type;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Term getRootTerm() {
		return rootTerm;
	}

	public void setRootTerm(Term rootTerm) {
		this.rootTerm = rootTerm;
	}

	public Term getGP() {
		return gp;
	}

	public void setGP(Term gp) {
		this.gp = gp;
	}

	public Term getMF() {
		return mf;
	}

	public void setMF(Term mf) {
		this.mf = mf;
	}

	public Term getBP() {
		return bp;
	}

	public void setBP(Term bp) {
		this.bp = bp;
	}

	public Term getCC() {
		return cc;
	}

	public void setCC(Term cc) {
		this.cc = cc;
	}

	public Set<Evidence> getEvidences() {
		return evidences;
	}

	public void setEvidences(Set<Evidence> evidences) {
		this.evidences = evidences;
	}

	public Set<Term> getTerms() {
		return terms;
	}

	public void setTerms(Set<Term> terms) {
		this.terms = terms;
	}

	public Set<TermAssociation> getTermAssociations() {
		return termAssociations;
	}

	public void setTermAssociations(Set<TermAssociation> termAssociations) {
		this.termAssociations = termAssociations;
	}

}