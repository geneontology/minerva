package org.geneontology.minerva.lookup;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.bbop.golr.java.RetrieveGolrBioentities;
import org.bbop.golr.java.RetrieveGolrOntologyClass;
import org.bbop.golr.java.RetrieveGolrBioentities.GolrBioentityDocument;
import org.bbop.golr.java.RetrieveGolrOntologyClass.GolrOntologyClassDocument;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.IRI;

public class GolrExternalLookupService implements ExternalLookupService {
	
	private final static Logger LOG = Logger.getLogger(GolrExternalLookupService.class);
	
	private final RetrieveGolrBioentities bioentityClient;
	private final RetrieveGolrOntologyClass ontologyClient;

	private final String golrUrl;

	private final CurieHandler curieHandler;
	
	public GolrExternalLookupService(String golrUrl, CurieHandler curieHandler) {
		this(golrUrl, curieHandler, false);
	}
	
	public GolrExternalLookupService(String golrUrl, CurieHandler curieHandler, final boolean logGolrRequests) {
		this(golrUrl, new RetrieveGolrBioentities(golrUrl, 2){

			@Override
			protected void logRequest(URI uri) {
				if(logGolrRequests) {
					LOG.info("Golr bioentity request: "+uri);
				}
			}
			
		}, new RetrieveGolrOntologyClass(golrUrl, 2) {

			@Override
			protected void logRequest(URI uri) {
				if(logGolrRequests) {
					LOG.info("Golr ontology cls request: "+uri);
				}
			}
			
		}, curieHandler);
		LOG.info("Creating Golr lookup service for minerva: "+golrUrl);
	}
	
	protected GolrExternalLookupService(String golrUrl, RetrieveGolrBioentities bioentityClient,
			RetrieveGolrOntologyClass ontologyClient, CurieHandler curieHandler) {
		this.bioentityClient = bioentityClient;
		this.ontologyClient = ontologyClient;
		this.golrUrl = golrUrl;
		this.curieHandler = curieHandler;
	}
	
	@Override
	public List<LookupEntry> lookup(IRI id) {
		String curie = curieHandler.getCuri(id);
		if (LOG.isDebugEnabled()) {
			LOG.debug("Golr look up for id: "+id+" curie: "+curie);
		}
		List<LookupEntry> result = new ArrayList<LookupEntry>();
		try {
			List<GolrBioentityDocument> bioentites = bioentityClient.getGolrBioentites(curie);
			if (bioentites != null && !bioentites.isEmpty()) {
				result = new ArrayList<ExternalLookupService.LookupEntry>(bioentites.size());
				for(GolrBioentityDocument doc : bioentites) {
					result.add(new LookupEntry(id, doc.bioentity_label, doc.type, doc.taxon));
				}
			}
			else if (ontologyClient != null){
				List<GolrOntologyClassDocument> ontologyEntities = ontologyClient.getGolrOntologyCls(curie);
				if (ontologyEntities != null && !ontologyEntities.isEmpty()) {
					result = new ArrayList<ExternalLookupService.LookupEntry>(ontologyEntities.size());
					for(GolrOntologyClassDocument doc : ontologyEntities) {
						result.add(new LookupEntry(id, doc.annotation_class_label, "ontology_class", null));
					}
				}
			}
		}
		catch(IOException exception) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Error during retrieval for id: "+id+" GOLR-URL: "+golrUrl, exception);
			}
			return null;
		}
		catch (Throwable exception) {
			LOG.warn("Unexpected problem during Golr lookup for id: "+id, exception);
			throw exception;
		}
		return result;
	}

	@Override
	public LookupEntry lookup(IRI id, String taxon) {
		throw new RuntimeException("This method is not implemented.");
	}

	@Override
	public String toString() {
		return "Golr: "+golrUrl;
	}

	
}
