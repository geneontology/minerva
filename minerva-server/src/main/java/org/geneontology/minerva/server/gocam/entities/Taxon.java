package org.geneontology.minerva.server.gocam.entities;

public class Taxon {
	private String name;
	private String label;

	public Taxon(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

}