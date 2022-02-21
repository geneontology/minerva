package org.geneontology.minerva.validation;


public class OWLValidationReport extends ModelValidationReport {
    public static final String report_type_id = "OWL_REASONER";
    public static final String tracker = "https://github.com/geneontology/helpdesk/issues";
    public static final String rulefile = "https://github.com/geneontology/go-ontology";


    public OWLValidationReport() {
        super(report_type_id, tracker, rulefile);
    }


    public String getAsText() {
        String e = "A human readable explanation of any OWL inconsistencies ought to go here.";
        return e;
    }

}
