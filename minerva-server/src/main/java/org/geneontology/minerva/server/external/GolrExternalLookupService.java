package org.geneontology.minerva.server.external;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.bbop.golr.java.RetrieveGolrBioentities;
import org.bbop.golr.java.RetrieveGolrBioentities.GolrBioentityDocument;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.IRI;

public class GolrExternalLookupService implements ExternalLookupService {
	
	private final static Logger LOG = Logger.getLogger(GolrExternalLookupService.class);
	
	private final RetrieveGolrBioentities client;

	private final String golrUrl;

	private final CurieHandler curieHandler;
	
	public GolrExternalLookupService(String golrUrl, CurieHandler curieHandler) {
		this(golrUrl, new RetrieveGolrBioentities(golrUrl, 2){

			@Override
			protected void logRequest(URI uri) {
				if(LOG.isDebugEnabled()) {
					LOG.debug("Golr request: "+uri);
				}
			}
			
		}, curieHandler);
		LOG.info("Creating Golr lookup service for minerva: "+golrUrl);
	}
	
	protected GolrExternalLookupService(String golrUrl, RetrieveGolrBioentities client, CurieHandler curieHandler) {
		this.client = client;
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
			List<GolrBioentityDocument> bioentites = client.getGolrBioentites(curie);
			if (bioentites != null && !bioentites.isEmpty()) {
				result = new ArrayList<ExternalLookupService.LookupEntry>(bioentites.size());
				for(GolrBioentityDocument doc : bioentites) {
					result.add(new LookupEntry(id, doc.bioentity_label, doc.type, doc.taxon));
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
