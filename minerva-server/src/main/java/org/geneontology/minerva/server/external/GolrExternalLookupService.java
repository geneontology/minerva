package org.geneontology.minerva.server.external;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.bbop.golr.java.RetrieveGolrBioentities;
import org.bbop.golr.java.RetrieveGolrBioentities.GolrBioentityDocument;

public class GolrExternalLookupService implements ExternalLookupService {
	
	private final static Logger LOG = Logger.getLogger(GolrExternalLookupService.class);
	
	private final RetrieveGolrBioentities client;

	private String golrUrl;
	
	public GolrExternalLookupService(String golrUrl) {
		this(new RetrieveGolrBioentities(golrUrl, 2){

			@Override
			protected void logRequest(URI uri) {
				if(LOG.isDebugEnabled()) {
					LOG.debug("Golr request: "+uri);
				}
			}
			
		});
		this.golrUrl = golrUrl;
	}
	
	protected GolrExternalLookupService(RetrieveGolrBioentities client) {
		this.client = client;
	}

	@Override
	public List<LookupEntry> lookup(String id) {
		List<LookupEntry> result = new ArrayList<LookupEntry>();
		try {
			List<GolrBioentityDocument> bioentites = client.getGolrBioentites(id);
			if (bioentites != null && !bioentites.isEmpty()) {
				result = new ArrayList<ExternalLookupService.LookupEntry>(bioentites.size());
				for(GolrBioentityDocument doc : bioentites) {
					result.add(new LookupEntry(doc.bioentity, doc.bioentity_label, doc.type, doc.taxon));
				}
			}
		}
		catch(IOException exception) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Error during retrieval for id: "+id+" GOLR-URL: "+golrUrl, exception);
			}
			return null;
		}
		return result;
	}

	@Override
	public LookupEntry lookup(String id, String taxon) {
		throw new RuntimeException("This method is not implemented.");
	}

}
