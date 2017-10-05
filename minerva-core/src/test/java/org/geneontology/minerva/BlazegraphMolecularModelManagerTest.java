package org.geneontology.minerva;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.CatalogXmlIRIMapper;
import owltools.io.ParserWrapper;

public class BlazegraphMolecularModelManagerTest extends OWLToolsTestBasics {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	/**
	 * Test whether the revised import function properly digests turtle files. We import a ttl file 
	 * into the BlazegraphMolecularModel, dump the model into files, and then compare these files. 
	 * Check this pull request for more information: https://github.com/geneontology/minerva/pull/144/files 
	 *
	 * @throws OWLOntologyCreationException
	 * @throws IOException
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 * @throws RepositoryException 
	 * @throws OWLOntologyStorageException 
	 */
	@Test
	public void testImportDump() throws OWLOntologyCreationException, IOException, RepositoryException, RDFParseException, RDFHandlerException, OWLOntologyStorageException {
		/* A path of the temporary journal file for Blazegraph storage system */
		String journalPath = folder.newFile().getAbsolutePath();
		/* A root path of the temporary directory */
		String tempRootPath = folder.getRoot().getAbsolutePath();
		/* I used the file from one of the turtle file in https://github.com/geneontology/noctua-models/blob/master/models/0000000300000001.ttl */
		String sourceModelPath = "src/test/resources/dummy-noctua-model.ttl";

		final ParserWrapper pw1 = new ParserWrapper();
		pw1.addIRIMapper(new CatalogXmlIRIMapper(new File("src/test/resources/mmg/catalog-v001.xml")));
		OWLGraphWrapper g = pw1.parseToOWLGraph(getResourceIRIString("mmg/basic-tbox-importer.omn"));
		BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(g, "http:/model.geneontology.org/", journalPath, tempRootPath);

		/* Import the test turtle file */
		m3.importModelToDatabase(new File(sourceModelPath));
		/* Dump back triples in the model to temporary files */
		for (IRI modelId : m3.getStoredModelIds())
			m3.dumpStoredModel(modelId, folder.getRoot());
		
		/* 
		 * Dump files often have different orders of triples compared with the ones in the original file, 
		 * thus one-by-one comparison is obviously not working here. We therefore leverage Jena's model, i.e.,  
		 * import original file and dump files using Jena and then compare them using Jena's isIsomorphicWith function. 
		 */
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(sourceModelPath);
		
		Model targetModel = ModelFactory.createDefaultModel();
		for (IRI modelId : m3.getStoredModelIds())
			targetModel.read(tempRootPath + File.separator + modelId + ".ttl");
		
		/* Does the dumped file contain all triples from the source file (and vice versa)? */
		if (sourceModel.isIsomorphicWith(targetModel) != true)
			fail("Source graphs and target graphs are not isomorphic.");
	}
}