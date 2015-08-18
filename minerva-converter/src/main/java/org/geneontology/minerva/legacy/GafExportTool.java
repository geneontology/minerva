package org.geneontology.minerva.legacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import owltools.gaf.BioentityDocument;
import owltools.gaf.GafDocument;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.gaf.io.GafWriter;
import owltools.gaf.io.GpadWriter;
import owltools.graph.OWLGraphWrapper;

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
	 * @param useModuleReasoner
	 * @param format format name or null for default
	 * @return modelContent
	 * @throws IOException
	 * @throws OWLOntologyCreationException
	 */
	public String exportModelLegacy(ModelContainer model, CurieHandler curieHandler, boolean useModuleReasoner, String format) throws IOException, OWLOntologyCreationException {
		final OWLOntology aBox = model.getAboxOntology();
		OWLReasoner r;
		if (useModuleReasoner) {
			r = model.getModuleReasoner();
		}
		else {
			r = model.getReasoner();
		}
		
		LegoAllIndividualToGeneAnnotationTranslator translator = new LegoAllIndividualToGeneAnnotationTranslator(new OWLGraphWrapper(model.getTboxOntology()), curieHandler, r, ecoMapper);
		Pair<GafDocument,BioentityDocument> pair = translator.translate(model.getModelId().toString(), aBox, null);
		ByteArrayOutputStream outputStream = null;
		try {
			outputStream = new ByteArrayOutputStream();
			if (format == null || "gaf".equalsIgnoreCase(format)) {
				// GAF
				GafWriter writer = new GafWriter();
				try {
					writer.setStream(new PrintStream(outputStream));
					GafDocument gafdoc = pair.getLeft();
					writer.write(gafdoc);
				}
				finally {
					writer.close();
				}

			}
			else if ("gpad".equalsIgnoreCase(format)) {
				// GPAD version 1.2
				GpadWriter writer = new GpadWriter(new PrintWriter(outputStream) , 1.2);
				writer.write(pair.getLeft());
			}
			else {
				throw new IOException("Unknown legacy format: "+format);
			}
			return outputStream.toString();
		}
		finally {
			IOUtils.closeQuietly(outputStream);
		}
	}
}
