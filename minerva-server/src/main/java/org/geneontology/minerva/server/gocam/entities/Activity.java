package org.geneontology.minerva.server.gocam.entities;

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

	public Activity(Term rootTerm) {
		this.type = ActivityType.ACTIVITY_UNIT;
		this.uuid = rootTerm.getUuid();
		this.title = rootTerm.getLabel();

		this.rootTerm = rootTerm;
		this.mf = rootTerm;
	}

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

	public void traverse(JsonOwlIndividual[] individuals, JsonOwlFact[] facts) {
		for (JsonOwlFact fact : facts) {

			if (fact.property == RelationType.ENABLED_BY.id && fact.subject == mf.getUuid()) {

				JsonOwlIndividual individual = GOCamTools.getNode(individuals, fact.object);
				if (individual != null) {
					JsonOwlObject[] types = individual.type;
					if (types.length > 0) {
						JsonOwlObject typeObj = types[0];
						Term term = new Term(individual.id, typeObj, individual.annotations);
						this.setGP(term);
					}
				}
			}
		}
	}

}