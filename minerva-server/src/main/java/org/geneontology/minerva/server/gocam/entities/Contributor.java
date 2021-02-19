package org.geneontology.minerva.server.gocam.entities;

public class Contributor {
	private String orcid;
	private String name;

	public Contributor(String orcid) {
		this.orcid = orcid;
	}

	public String getOrcid() {
		return orcid;
	}

	public void setOrcid(String orcid) {
		this.orcid = orcid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}