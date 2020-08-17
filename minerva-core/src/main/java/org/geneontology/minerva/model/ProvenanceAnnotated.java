package org.geneontology.minerva.model;

import java.util.Set;

public class ProvenanceAnnotated {

	Set<String> contributors;
	String date;
	Set<String> provided_by;
	Set<String> comments;
	Set<String> notes;

	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public Set<String> getComments() {
		return comments;
	}
	public void setComments(Set<String> comments) {
		this.comments = comments;
	}
	public Set<String> getNotes() {
		return notes;
	}
	public void setNotes(Set<String> notes) {
		this.notes = notes;
	}
	public Set<String> getContributors() {
		return contributors;
	}
	public void setContributors(Set<String> contributors) {
		this.contributors = contributors;
	}
	public Set<String> getProvided_by() {
		return provided_by;
	}
	public void setProvided_by(Set<String> provided_by) {
		this.provided_by = provided_by;
	}
	
	
}

