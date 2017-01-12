package org.geneontology.minerva.legacy.sparql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.log4j.Logger;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.semanticweb.owlapi.model.IRI;

public class GPADSPARQLExport {

	private static final Logger LOG = Logger.getLogger(GPADSPARQLExport.class);
	private static String query;
	static {
		try {
			query = IOUtils.toString(GPADSPARQLExport.class.getResourceAsStream("gpad.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not load SPARQL query from jar", e);
		}
	}

	private final CurieHandler curieHandler;
	private final Map<IRI, String> relationShorthandIndex;
	private final ExternalLookupService lookupService;

	public GPADSPARQLExport(CurieHandler handler, ExternalLookupService lookup, Map<IRI, String> shorthandIndex) {
		this.curieHandler = handler;
		this.relationShorthandIndex = shorthandIndex;
		this.lookupService = lookup;
	}

	public String exportGPAD(Model model) {
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		Set<GPADData> annotations = new HashSet<>();
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			// When using GROUP_BY Jena may return a single empty result instead of none
			if (qs.get("pr_type") != null) {
				annotations.add(new DefaultGPADData(qs));
			}
		}
		qe.close();
		return new GPADRenderer(curieHandler, lookupService, relationShorthandIndex).renderAll(annotations);
	}

}
