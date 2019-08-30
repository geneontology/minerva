package org.geneontology.minerva.server.validation;

public class OWLValidationReport extends ModelValidationReport {
	public static final String report_type_id = "OWL_REASONER";
	public static final String tracker = "https://github.com/geneontology/helpdesk/issues";
	public static final String rulefile = "https://github.com/geneontology/go-ontology";
	

	
	public OWLValidationReport() {
		super(report_type_id, tracker, rulefile);
	}

}
