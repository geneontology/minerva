package org.geneontology.minerva.legacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.ModelContainer;
import org.semanticweb.owlapi.model.OWLOntology;

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
	 * @param modelId
	 * @param model
	 * @param format format name or null for default
	 * @return modelContent
	 * @throws IOException
	 */
	public String exportModelLegacy(String modelId, ModelContainer model, String format) throws IOException {
		final OWLOntology aBox = model.getAboxOntology();
		LegoToGeneAnnotationTranslator translator = new LegoToGeneAnnotationTranslator(new OWLGraphWrapper(model.getTboxOntology()), model.getReasoner(), ecoMapper);
		Pair<GafDocument,BioentityDocument> pair = translator.translate(modelId, aBox, null);
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
