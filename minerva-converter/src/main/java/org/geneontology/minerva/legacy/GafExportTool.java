package org.geneontology.minerva.legacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.gaf.GafDocument;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.gaf.io.GafWriter;
import owltools.gaf.io.GpadWriter;

public class GafExportTool {

	private static volatile GafExportTool INSTANCE = null;
	
	private final SimpleEcoMapper ecoMapper;
	
	private GafExportTool(SimpleEcoMapper mapper) {
		this.ecoMapper = mapper;
	}
	
	public static synchronized GafExportTool getInstance() throws IOException {
		if (INSTANCE == null) {
			INSTANCE = new GafExportTool(EcoMapperFactory.createSimple());
		}
		return INSTANCE;
	}

	/**
	 * Export the model (ABox) in a legacy format, such as GAF or GPAD.
	 * 
	 * @param model
	 * @param curieHandler
	 * @param lookup
	 * @param formats set of format names
	 * @return modelContent
	 * @throws OWLOntologyCreationException
	 * @throws UnknownIdentifierException 
	 */
	public Map<String, String> exportModelLegacy(ModelContainer model, CurieHandler curieHandler, ExternalLookupService lookup, Set<String> formats) throws OWLOntologyCreationException, UnknownIdentifierException {
		final OWLOntology aBox = model.getAboxOntology();
		
		LegoToGeneAnnotationTranslator translator = new LegoToGeneAnnotationTranslator(aBox, curieHandler, ecoMapper);
		GafDocument gafdoc = translator.translate(model.getModelId().toString(), aBox, lookup, null);
		Map<String, String> exportResults = new HashMap<String, String>();
		for(String format : formats) {
			ByteArrayOutputStream outputStream = null;
			try {
				outputStream = new ByteArrayOutputStream();
				if ("gaf".equalsIgnoreCase(format)) {
					// GAF
					GafWriter writer = new GafWriter();
					try {
						writer.setStream(new PrintStream(outputStream));
						writer.write(gafdoc);
					}
					finally {
						IOUtils.closeQuietly(writer);
					}

				}
				else if ("gpad".equalsIgnoreCase(format)) {
					// GPAD version 1.2
					GpadWriter writer = new GpadWriter(new PrintWriter(outputStream) , 1.2);
					writer.write(gafdoc);
				}
				else {
					continue;
				}
				String exported = outputStream.toString();
				exportResults.put(format, exported);
			}
			finally {
				IOUtils.closeQuietly(outputStream);
			}
		}
		return exportResults;
	}
}
