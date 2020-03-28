package org.geneontology.minerva.server.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
			class Taxon {
				String id;
				String label;
				public Taxon(String id, String label) {
					super();
					this.id = id;
					this.label = label;
				}
			}
			public Set<Taxon> taxa;
			public Taxa(Map<String, String> id_label) {
				if(id_label!=null) {
					taxa = new HashSet<Taxon>();
					for(String id : id_label.keySet()) {
						Taxon t = new Taxon(id, id_label.get(id));
						taxa.add(t);
					}
				}
			}
		}
		
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		public Taxa get() {	
			Map<String, String> id_label =  new HashMap<String, String>();
			for(String t : m3.getTaxon_models().keySet()) {
				String label = "";
				try {
					label = m3.getGolego_repo().getLabel(t);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String tcurie = t.replace("http://purl.obolibrary.org/obo/NCBITaxon_", "NCBITaxon:");
				id_label.put(tcurie, label);
			}
			return new Taxa(id_label);
		}

		public BlazegraphMolecularModelManager<?> getM3() {
			return m3;
		}
		
	}