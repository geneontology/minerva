package org.geneontology.minerva.server.handler;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.semanticweb.owlapi.model.IRI;

	
	/**
	 * Respond to queries about taxa in minerva world 
	 *
	 */
	@Path("/taxa")
	public class TaxonHandler {
		private final BlazegraphMolecularModelManager<?> m3;
		/**
		 * @param ont_annos 
		 * @param started_at 
		 * 
		 */
		public TaxonHandler(BlazegraphMolecularModelManager<?> m3) {
			this.m3 = m3;
		}

		public class Taxa {
			public Set<String> taxa;
			public Taxa(Set<String> used_taxa) {
				taxa = used_taxa;
			}
		}
		
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		public Taxa get() {	
			Set<String> taxa =  new HashSet<String>();
			for(String t : m3.getTaxon_models().keySet()) {
				String tcurie = t.replace("http://purl.obolibrary.org/obo/NCBITaxon_", "NCBITaxon:");
				taxa.add(tcurie);
			}
			return new Taxa(taxa);
		}
		
	}