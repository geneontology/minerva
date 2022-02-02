package org.geneontology.minerva.server.validation;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.GolrExternalLookupService;
import org.geneontology.minerva.server.StartUpTool;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.validation.ValidationResultSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.google.common.collect.Sets;

public class ValidationTest {
	private static final Logger LOGGER = Logger.getLogger(ValidationTest.class);
	static final String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
	static final String go_lego_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static final String catalog = "src/test/resources/ontology/catalog-for-validation.xml";
	//add something like this to the catalog to replace the download step for local testing
	// <uri id="loading local go-lego" name="http://purl.obolibrary.org/obo/go/extensions/go-lego.owl" uri="file:///tmp/go-lego.owl"/>
	static final String modelIdcurie = "http://model.geneontology.org/";
	static final String modelIdPrefix = "gomodel";
	static final String shexFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shex";
	static final String goshapemapFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shapeMap";
	static OWLOntology tbox_ontology;
	static CurieHandler curieHandler;

	@ClassRule
	public static TemporaryFolder tmp = new TemporaryFolder();

	@BeforeClass
	public static void setUpBeforeClass() {
		CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.loadDefaultMappings(), localMappings);

		LOGGER.info("loading tbox ontology: "+ontologyIRI);
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		LOGGER.info("using catalog: "+catalog);
		try {
			ontman.setIRIMappers(Sets.newHashSet(new owltools.io.CatalogXmlIRIMapper(catalog)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			tbox_ontology = ontman.loadOntology(IRI.create(ontologyIRI));
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		LOGGER.info("tbox ontologies loaded: "+tbox_ontology.getAxiomCount());
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

//	@Test
	public void testTmpValid() {
		String valid_model_folder = "src/test/resources/models/tmp/";
		testJournalLoad(valid_model_folder);
		boolean should_fail = false;
		boolean check_shex = true;
		try {
			validateGoCams(
					valid_model_folder, 
					should_fail, //models should fail check
					check_shex //check shex (false just OWL)
					);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	@Test
	public void testValid() {
		String valid_model_folder = "src/test/resources/models/should_pass/";
		boolean should_fail = false;
		boolean check_shex = true;
		try {
			validateGoCams(
					valid_model_folder, 
					should_fail, //models should fail check
					check_shex //check shex (false just OWL)
					);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	@Test
	public void testValidLoad() {
		String valid_model_folder = "src/test/resources/models/should_pass/";
		testJournalLoad(valid_model_folder);
	}
	
	@Test
	public void testInValid() {
		String valid_model_folder = "src/test/resources/models/should_fail/";
		testJournalLoad(valid_model_folder);
		boolean should_fail = true;
		boolean check_shex = true;
		try {
			validateGoCams(
					valid_model_folder, 
					should_fail, //models should fail check
					check_shex //check shex (false just OWL)
					);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	@Test
	public void testInValidLoad() {
		String valid_model_folder = "src/test/resources/models/should_fail/";
		testJournalLoad(valid_model_folder);
	}

	public static void validateGoCams(String input, boolean should_fail, boolean check_shex) throws Exception {

		String blazegraph_journal = makeBlazegraphJournal(input);
		UndoAwareMolecularModelManager m3 = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, modelIdPrefix, blazegraph_journal, null, go_lego_journal_file, true);
		try {
			URL shex_schema_url = new URL(shexFileUrl);
			File shex_schema_file = new File("src/test/resources/validate.shex"); //for some reason the temporary_model file won't parse..
			org.apache.commons.io.FileUtils.copyURLToFile(shex_schema_url, shex_schema_file);			

			URL shex_map_url = new URL(goshapemapFileUrl);
			File shex_map_file = new File("src/test/resources/validate.shapemap");
			org.apache.commons.io.FileUtils.copyURLToFile(shex_map_url, shex_map_file);

			MinervaShexValidator shex = new MinervaShexValidator(shex_schema_file, shex_map_file, curieHandler, m3.getGolego_repo());
			if(check_shex) {
				if(check_shex) {
					shex.setActive(true);
				}else {
					shex.setActive(false);
				}
			}
			InferenceProviderCreator ipc = StartUpTool.createInferenceProviderCreator("arachne", m3, shex);
			LOGGER.info("Validating models:");
			m3.getAvailableModelIds().stream().forEach(modelIRI -> {
				boolean isConsistent = true;
				boolean isConformant = true;
				LOGGER.info("processing \t"+modelIRI);

				ModelContainer mc = m3.getModel(modelIRI);	
				Set<OWLAnnotation> annos = mc.getAboxOntology().getAnnotations();
				//this is where everything actually happens
				InferenceProvider ip;
				try {
					//this ipc.create method results in the execution of the OWL reasoner and, if shex is set to active, the shex validation
					ip = ipc.create(mc);
					isConsistent = ip.isConsistent();
					if(!should_fail) {
						assertTrue(modelIRI+" is assessed to be (OWL) inconsistent but should not be.", isConsistent);
					}else if(!check_shex) {
						assertFalse(modelIRI+" is assessed to be (OWL) consistent but should not be.", isConsistent);
					}
					if(check_shex) {
						ValidationResultSet validations = ip.getValidation_results();
						isConformant = validations.allConformant();	
						if(!should_fail) {
							assertTrue(modelIRI+" does not conform to the shex schema and it should: \n"+annos, isConformant);
						}else {
							assertFalse(modelIRI+" conforms to the shex schema and it should not: \n"+annos, isConformant);
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			LOGGER.info("done with validation");
		}finally {
			m3.dispose();
		}
	}

	public void testJournalLoad(String input_folder) {
		try {
			String inputDB = tmp.newFile().getAbsolutePath();
			File i = new File(input_folder);
			if(i.exists()) {
				//remove anything that existed earlier
				File bgdb = new File(inputDB);
				if(bgdb.exists()) {
					bgdb.delete();
				}		
				OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
				BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, inputDB, null, go_lego_journal_file, true);
				Map<String, String> file_iri = new HashMap<String, String>();
				Map<String, String> iri_file = new HashMap<String, String>();
				Set<String> model_iris = new HashSet<String>();
				if(i.isDirectory()) {
					FileUtils.listFiles(i, null, true).forEach(file-> {
						if(file.getName().endsWith(".ttl")||file.getName().endsWith("owl")) {
							try {
								String modeluri = m3.importModelToDatabase(file, true);	
								if(!model_iris.add(modeluri)) {
									String error = "\n"+file+"\n redundant iri "+modeluri+"\n with file "+iri_file.get(modeluri);
									assertFalse(error, true);
								}else {								
									file_iri.put(file.getName(), modeluri);
									iri_file.put(modeluri, file.getName());
								}
							} catch (OWLOntologyCreationException | RepositoryException | RDFParseException
									| RDFHandlerException | IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} 
						} 
					});
					if(model_iris.size()!=file_iri.size()) {

					}
					assertTrue("same model iri used more than once ", model_iris.size()==file_iri.size());
				}else {
					LOGGER.info("Loading " + i);
					m3.importModelToDatabase(i, true);
				}
				LOGGER.info("loaded files into blazegraph journal: "+input_folder);
				m3.dispose();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	private static String makeBlazegraphJournal(String input_folder) throws IOException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException {
		String inputDB = tmp.newFile().getAbsolutePath();
		File i = new File(input_folder);
		if(i.exists()) {
			//remove anything that existed earlier
			File bgdb = new File(inputDB);
			if(bgdb.exists()) {
				bgdb.delete();
			}
			//load everything into a bg journal
			OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
			BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, inputDB, null, go_lego_journal_file, true);
			if(i.isDirectory()) {
				FileUtils.listFiles(i, null, true).parallelStream().parallel().forEach(file-> {
					if(file.getName().endsWith(".ttl")||file.getName().endsWith("owl")) {
						try {
							String modeluri = m3.importModelToDatabase(file, true);	
							LOGGER.info("Loaded\t" + file+"\t"+modeluri);
						} catch (OWLOntologyCreationException | RepositoryException | RDFParseException
								| RDFHandlerException | IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 
					} 
				});
			}else {
				LOGGER.info("Loading " + i);
				m3.importModelToDatabase(i, true);
			}
			LOGGER.info("loaded files into blazegraph journal: "+input_folder);
			m3.dispose();
		}
		return inputDB;
	}

}