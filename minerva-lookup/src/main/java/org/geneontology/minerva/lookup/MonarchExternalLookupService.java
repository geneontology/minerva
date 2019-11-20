package org.geneontology.minerva.lookup;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.bbop.golr.java.RetrieveGolrOntologyClass;
import org.bbop.golr.java.RetrieveGolrOntologyClass.GolrOntologyClassDocument;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.IRI;

public class MonarchExternalLookupService implements ExternalLookupService {
	
	private final static Logger LOG = Logger.getLogger(MonarchExternalLookupService.class);
	
	private final RetrieveGolrOntologyClass monarchClient;

	private final String monarchUrl;

	private final CurieHandler curieHandler;
	
	public MonarchExternalLookupService(String monarchUrl, CurieHandler curieHandler) {
		this(monarchUrl, curieHandler, false);
	}
	
	public MonarchExternalLookupService(String monarchUrl, CurieHandler curieHandler, final boolean logGolrRequests) {
		this(monarchUrl, new RetrieveGolrOntologyClass(monarchUrl, 2) {

			@Override
			protected void logRequest(URI uri) {
				if(logGolrRequests) {
					LOG.info("Golr ontology cls request: "+uri);
				}
			}
			
		}, curieHandler);
		LOG.info("Creating Golr lookup service for minerva: "+monarchUrl);
	}
	
	protected MonarchExternalLookupService(String golrUrl, RetrieveGolrOntologyClass ontologyClient, CurieHandler curieHandler) {
		this.monarchClient = ontologyClient;
		this.monarchUrl = golrUrl;
		this.curieHandler = curieHandler;
	}
	
	@Override
	public List<LookupEntry> lookup(IRI id) {
		String curie = curieHandler.getCuri(id);
		if (LOG.isDebugEnabled()) {
			LOG.debug("Monarch Golr look up for id: "+id+" curie: "+curie);
		}
		List<LookupEntry> result = new ArrayList<LookupEntry>();
		try {
			if (monarchClient != null){
				List<GolrOntologyClassDocument> entities = monarchClient.getGolrOntologyCls(curie);
				if (entities != null && !entities.isEmpty()) {
					result = new ArrayList<ExternalLookupService.LookupEntry>(entities.size());
					for(GolrOntologyClassDocument doc : entities) {
						result.add(new LookupEntry(id, doc.annotation_class_label, "ontology_class", null, doc.isa_closure));
					}
				}
			}
		}
		catch(IOException exception) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Error during retrieval for id: "+id+" GOLR-URL: "+monarchUrl, exception);
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
		return "Monarch: "+monarchUrl;
	}

	
}
