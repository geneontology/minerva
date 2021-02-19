package org.geneontology.minerva.server.gocam.entities;

public class Group {
	private String url;
	private String name;

	public Group(String url) {
		this.url = url;
	}

	public Group(String url, String name) {
		this.url = url;
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}