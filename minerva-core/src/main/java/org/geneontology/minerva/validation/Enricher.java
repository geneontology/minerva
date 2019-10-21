/**
 * 
 */
package org.geneontology.minerva.validation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DC;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

/**
 * @author bgood
 *
 */
public class Enricher {
	public static final String go_endpoint = "http://rdf.geneontology.org/blazegraph/sparql";
	public String extra_info_endpoint = null;
	public OWLReasoner tbox_reasoner;
	/**
	 * 
	 */
	public Enricher(String extra_endpoint, OWLReasoner reasoner) {
		if(extra_endpoint != null) {
			extra_info_endpoint = extra_endpoint;
		}
		if(reasoner != null) {
			tbox_reasoner = reasoner;
		}
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws IOException, OWLOntologyCreationException {
		String dir = "/Users/bgood/Desktop/test/go_cams/reactome/reactome-homosapiens-SLBP_independent_Processing_of_Histone_Pre-mRNAs.ttl";
		Map<String,Model> name_model = loadRDF(dir);
		System.out.println("Start on "+name_model.size()+" models "+System.currentTimeMillis()/1000);
		
		String tbox_file_2 = "/Users/bgood/gocam_ontology/REO.owl";
		String tbox_file = "/Users/bgood/gocam_ontology/go-lego-merged-9-23-2019.owl";
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();	
		System.out.println("loading ontology");
		OWLOntology tbox = ontman.loadOntologyFromOntologyDocument(new File(tbox_file));
		System.out.println("done loading "+tbox_file);
		OWLOntology tbox2 = ontman.loadOntologyFromOntologyDocument(new File(tbox_file_2));
		System.out.println("done loading "+tbox_file_2);
		for(OWLAxiom a : tbox2.getAxioms()) {
			ontman.addAxiom(tbox, a);
		}
		System.out.println("done adding axioms from "+tbox_file_2);
		System.out.println("done loading, building reasoner");
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(tbox);

		System.out.println("done building reasoner, enriching");
		Enricher e = new Enricher(null, reasoner);
		for(String name : name_model.keySet()) {
			Model model = name_model.get(name);
			model = e.enrichSuperClasses(model);
			write(model, "/Users/bgood/Desktop/test/shex/enriched_go_lego_"+name);
			System.out.println("done with "+name+" "+System.currentTimeMillis()/1000);
		}
		System.out.println("Finish on "+name_model.size()+" models "+System.currentTimeMillis()/1000);

	}

	public Model enrichSuperClasses(Model model) {
		String getOntTerms = 
				"PREFIX owl: <http://www.w3.org/2002/07/owl#> "
						+ "SELECT DISTINCT ?term " + 
						"        WHERE { " + 
						"        ?ind a owl:NamedIndividual . " + 
						"        ?ind a ?term . " + 
						"        FILTER(?term != owl:NamedIndividual)" + 
						"        FILTER(isIRI(?term)) ." + 
						"        }";
		String terms = "";
		Set<String> term_set = new HashSet<String>();
		try{
			QueryExecution qe = QueryExecutionFactory.create(getOntTerms, model);
			ResultSet results = qe.execSelect();

			while (results.hasNext()) {
				QuerySolution qs = results.next();
				Resource term = qs.getResource("term");
				terms+=("<"+term.getURI()+"> ");
				term_set.add(term.getURI());
			}
			qe.close();
		} catch(QueryParseException e){
			e.printStackTrace();
		}
		//either get the superclasses from a reasoner here
		if(tbox_reasoner!=null) {
			for(String term : term_set) {
				OWLClass c = 
						tbox_reasoner.
						getRootOntology().
						getOWLOntologyManager().
						getOWLDataFactory().getOWLClass(IRI.create(term));
				Resource child = model.createResource(term);
				Set<OWLClass> supers = tbox_reasoner.getSuperClasses(c, false).getFlattened();
				for(OWLClass parent_class : supers) {
					Resource parent = model.createResource(parent_class.getIRI().toString());
					model.add(model.createStatement(child, org.apache.jena.vocabulary.RDFS.subClassOf, child));
					model.add(model.createStatement(child, org.apache.jena.vocabulary.RDFS.subClassOf, parent));
					model.add(model.createStatement(child, org.apache.jena.vocabulary.RDF.type, org.apache.jena.vocabulary.OWL.Class));
				}
			}
		//or get them from the remote endpoint(s)
		}else {
			String superQuery = ""
					+ "PREFIX owl: <http://www.w3.org/2002/07/owl#> "
					+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
					+ "CONSTRUCT { " + 
					"        ?term rdfs:subClassOf ?superclass ." + 
					"        ?term a owl:Class ." + 
					"        }" + 
					"        WHERE {" + 
					"        VALUES ?term { "+terms+" } " + 
					"        ?term rdfs:subClassOf* ?superclass ." + 
					"        FILTER(isIRI(?superclass)) ." + 
					"        }";

			Query query = QueryFactory.create(superQuery); 
			try ( 
					QueryExecution qexec = QueryExecutionFactory.sparqlService(go_endpoint, query) ) {
				qexec.execConstruct(model);
				qexec.close();
			} catch(QueryParseException e){
				e.printStackTrace();
			}
			if(extra_info_endpoint!=null) {
				try ( 
						QueryExecution qexec = QueryExecutionFactory.sparqlService(extra_info_endpoint, query) ) {
					qexec.execConstruct(model);
					qexec.close();
				} catch(QueryParseException e){
					e.printStackTrace();
				}
			}
		}
		return model;
	}

	public static void write(Model model, String outfilename) throws IOException {
		FileOutputStream o = new FileOutputStream(outfilename);
		model.write(o, "TURTLE");
		o.close();
	}

	public static Map<String, Model> loadRDF(String model_dir){
		Map<String, Model> name_model = new HashMap<String, Model>();
		File good_dir = new File(model_dir);
		if(good_dir.isDirectory()) {
			File[] good_files = good_dir.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".ttl");
				}
			});		
			for(File good_file : good_files) {
				Model model = ModelFactory.createDefaultModel() ;
				model.read(good_file.getAbsolutePath()) ;
				name_model.put(good_file.getName(), model);
			}	
		}else if(good_dir.getName().endsWith(".ttl")){
			Model model = ModelFactory.createDefaultModel() ;
			model.read(good_dir.getAbsolutePath()) ;
			name_model.put(good_dir.getName(), model);
		}
		return name_model;
	}
}
