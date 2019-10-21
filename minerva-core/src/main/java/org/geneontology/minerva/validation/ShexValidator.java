/**
 * 
 */
package org.geneontology.minerva.validation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.jena.JenaGraph;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDFS;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.abstrsynt.EachOf;
import fr.inria.lille.shexjava.schema.abstrsynt.NodeConstraint;
import fr.inria.lille.shexjava.schema.abstrsynt.RepeatedTripleExpression;
import fr.inria.lille.shexjava.schema.abstrsynt.Shape;
import fr.inria.lille.shexjava.schema.abstrsynt.ShapeAnd;
import fr.inria.lille.shexjava.schema.abstrsynt.ShapeExpr;
import fr.inria.lille.shexjava.schema.abstrsynt.ShapeExprRef;
import fr.inria.lille.shexjava.schema.abstrsynt.ShapeOr;
import fr.inria.lille.shexjava.schema.abstrsynt.TCProperty;
import fr.inria.lille.shexjava.schema.abstrsynt.TripleConstraint;
import fr.inria.lille.shexjava.schema.abstrsynt.TripleExpr;
import fr.inria.lille.shexjava.schema.parsing.GenParser;
import fr.inria.lille.shexjava.util.Pair;
import fr.inria.lille.shexjava.validation.RecursiveValidation;
import fr.inria.lille.shexjava.validation.Status;
import fr.inria.lille.shexjava.validation.Typing;

/**
 * @author bgood
 *
 */
public class ShexValidator {
	public ShexSchema schema;
	public Map<String, String> GoQueryMap;
	public OWLReasoner tbox_reasoner;
	public static final String endpoint = "http://rdf.geneontology.org/blazegraph/sparql";

	/**
	 * @throws Exception 
	 * 
	 */
	public ShexValidator(String shexpath, String goshapemappath) throws Exception {
		schema = GenParser.parseSchema(new File(shexpath).toPath());
		GoQueryMap = makeGoQueryMap(goshapemappath);
	}

	public ShexValidator(File shex_schema_file, File shex_map_file) throws Exception {
		schema = GenParser.parseSchema(shex_schema_file.toPath());
		GoQueryMap = makeGoQueryMap(shex_map_file.getAbsolutePath());
	}

	public static Map<String, String> makeGoQueryMap(String shapemap_file) throws IOException{ 
		Map<String, String> shapelabel_sparql = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new FileReader(shapemap_file));
		String line = reader.readLine();
		String all = line;
		while(line!=null) {
			all+=line;
			line = reader.readLine();			
		}
		reader.close();
		String[] maps = all.split(",");
		for(String map : maps) {
			String sparql = StringUtils.substringBetween(map, "'", "'");
			sparql = sparql.replace("a/", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?c . ?c ");
			String[] shapemaprow = map.split("@");
			String shapelabel = shapemaprow[1];
			shapelabel = shapelabel.replace(">", "");
			shapelabel = shapelabel.replace("<", "");
			shapelabel = shapelabel.trim();
			shapelabel_sparql.put(shapelabel, sparql);
		}
		return shapelabel_sparql;
	}
	
	public ShexValidationReport runShapeMapValidation(Model test_model, boolean stream_output) throws Exception {
		String model_title = getModelTitle(test_model);
		ShexValidationReport r = new ShexValidationReport(null, test_model);	
		RDF rdfFactory = new SimpleRDF();
		JenaRDF jr = new JenaRDF();
		//this shex implementation likes to use the commons JenaRDF interface, nothing exciting here
		JenaGraph shexy_graph = jr.asGraph(test_model);
		//recursive only checks the focus node against the chosen shape.  
		RecursiveValidation shex_recursive_validator = new RecursiveValidation(schema, shexy_graph);
		//for each shape in the query map (e.g. MF, BP, CC, etc.)
		boolean all_good = true;
		for(String shapelabel : GoQueryMap.keySet()) {
			//not quite the same pattern as the other shapes
			//TODO needs more work 
			if(shapelabel.equals("http://purl.obolibrary.org/obo/go/shapes/AnnotatedEdge")) {
				continue;
			}
			
			Label shape_label = new Label(rdfFactory.createIRI(shapelabel));
			//get the nodes in this model that SHOULD match the shape
			Set<Resource> focus_nodes = getFocusNodesBySparql(test_model, GoQueryMap.get(shapelabel));
			for(Resource focus_node_resource : focus_nodes) {
				if(focus_node_resource==null) {
					System.out.println("null focus node for shape "+shape_label);
					continue;
				}
				RDFTerm focus_node = null;
				String focus_node_id = "";
				if(focus_node_resource.isURIResource()) {
					focus_node = rdfFactory.createIRI(focus_node_resource.getURI());
					focus_node_id = focus_node_resource.getURI();
				}else {
					focus_node = rdfFactory.createBlankNode(focus_node_resource.getId().getLabelString());
					focus_node_id = focus_node_resource.getId().getLabelString();
				}
				//deal with curies for output
				String node = focus_node_id;
				node = getPreferredId(node, focus_node_resource);
//				if(curieHandler!=null) {
//					node = curieHandler.getCuri(IRI.create(focus_node_resource.getURI()));
//				}
				//check the node against the intended shape
				shex_recursive_validator.validate(focus_node, shape_label);
				Typing typing = shex_recursive_validator.getTyping();
				//capture the result
				Status status = typing.getStatus(focus_node, shape_label);
				if(status.equals(Status.CONFORMANT)) {
					Set<String> shape_ids = r.node_matched_shapes.get(node);
					if(shape_ids==null) {
						shape_ids = new HashSet<String>();
					}
					shape_ids.add(shapelabel);				
					r.node_matched_shapes.put(node, shape_ids);
				}else if(status.equals(Status.NONCONFORMANT)) {
					//if any of these tests is invalid, the model is invalid
					all_good = false;
					//implementing a start on a generic violation report structure here
					ShexViolation violation = new ShexViolation(node);				 					
					ShexExplanation explanation = new ShexExplanation();
					explanation.setShape(shapelabel);				
					Set<ShexConstraint> unmet_constraints = getUnmetConstraints(focus_node_resource, shapelabel, test_model);				
					for(ShexConstraint constraint : unmet_constraints) {
						explanation.addConstraint(constraint);
						violation.addExplanation(explanation);
					}	
					r.addViolation(violation);
					String error = r.getAsText(); 
					if(stream_output) {
						System.out.println("Invalid model:"+model_title+"\n\t"+error);
					}				
				}else if(status.equals(Status.NOTCOMPUTED)) {
					//if any of these are not computed, there is a problem
					String error = focus_node_id+" was not tested against "+shapelabel;
					if(stream_output) {
						System.out.println("Invalid model:"+model_title+"\n\t"+error);
					}
				}
			}
		}
		if(all_good) {
			r.conformant = true;
		}else {
			r.conformant = false;
		}
		return r;
	}

	public static Set<Resource> getFocusNodesBySparql(Model model, String sparql){
		Set<Resource> nodes = new HashSet<Resource>();
		QueryExecution qe = QueryExecutionFactory.create(sparql, model);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource node = qs.getResource("x");
			nodes.add(node);
		}
		qe.close();
		return nodes;
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
					QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query) ) {
				qexec.execConstruct(model);
				qexec.close();
			} catch(QueryParseException e){
				e.printStackTrace();
			}
		}
		return model;
	}

	
	private Set<ShexConstraint> getUnmetConstraints(Resource focus_node, String shape_id, Model model) {
		Set<ShexConstraint> unmet_constraints = new HashSet<ShexConstraint>();
		String explanation = "";
		RDF rdfFactory = new SimpleRDF();
		Label shape_label = new Label(rdfFactory.createIRI(shape_id));
		ShapeExpr rule = schema.getRules().get(shape_label);
		//get a map from properties to expected shapes of the asserted objects of the property
		Map<String, Set<String>> expected_property_ranges = getPropertyRangeMap(rule, null);
		//get a map from properties to actual shapes of the asserted objects
		JenaRDF jr = new JenaRDF();
		JenaGraph shexy_graph = jr.asGraph(model); 
		RecursiveValidation shex_recursive_validator = new RecursiveValidation(schema, shexy_graph);

		//get the focus node in the rdf model
		//check for assertions with properties in the target shape
		for(String prop_uri : expected_property_ranges.keySet()) {
			if(prop_uri.equals(org.apache.jena.vocabulary.RDF.type.getURI())){
				continue;//TODO types need more work..
			}
			Property prop = model.getProperty(prop_uri);
			//checking on objects of this property for the problem node.
			for (StmtIterator i = focus_node.listProperties(prop); i.hasNext(); ) {
				RDFNode obj = i.nextStatement().getObject();
				//check the computed shapes for this individual
				if(!obj.isResource()) {
					continue;
					//no checks on literal values at this time
				}
				RDFTerm range_obj = rdfFactory.createIRI(obj.asResource().getURI());
				//does it hit any allowable shapes?
				boolean good = false;
				for(String target_shape_uri : expected_property_ranges.get(prop_uri)) {
					Label target_shape_label = new Label(rdfFactory.createIRI(target_shape_uri));
					shex_recursive_validator.validate(range_obj, target_shape_label);
					//could use refine to get all of the actual shapes - but would want to do this
					//once per validation...
					//RefineValidation shex_refine_validator = new RefineValidation(schema, shexy_graph);
					Typing shape_test = shex_recursive_validator.getTyping();
					Pair<RDFTerm, Label> p = new Pair<RDFTerm, Label>(range_obj, target_shape_label);
					Status r = shape_test.getStatusMap().get(p);
					if(r.equals(Status.CONFORMANT)) {
						good = true;
						break;
					}
				}
				if(!good) {
					String object = obj.toString();
					String property = prop.toString();
					object = getPreferredId(object, IRI.create(object));
					property = getPreferredId(property, IRI.create(property));
//					if(curieHandler!=null) {
//						object = curieHandler.getCuri(IRI.create(object));
//						property = curieHandler.getCuri(IRI.create(property));
//					}
					explanation+="\n"+object+" range of "+property+"\n\tshould match one of the following shapes but does not: \n\t\t"+expected_property_ranges.get(prop_uri);
					ShexConstraint constraint = new ShexConstraint(object, property, expected_property_ranges.get(prop_uri));
					unmet_constraints.add(constraint);
				}
			}
		}
		return unmet_constraints;
	}
	
	
	/**
	 * If implementation has something like a curie handler and preference, override these methods
	 * @param node
	 * @param iri
	 * @return
	 */
	public String getPreferredId(String node, IRI iri) {
		return node;
	}

	public String getPreferredId(String node, Resource resource) {
		return node;
	}
	
	public static Map<String, Set<String>> getPropertyRangeMap(ShapeExpr expr, Map<String, Set<String>> prop_range){
		if(prop_range==null) {
			prop_range = new HashMap<String, Set<String>>();
		}

		if(expr instanceof ShapeAnd) {
			ShapeAnd andshape = (ShapeAnd)expr;
			for(ShapeExpr subexp : andshape.getSubExpressions()) {
				prop_range = getPropertyRangeMap(subexp, prop_range);
			}
		}
		else if (expr instanceof ShapeOr) {
			//			explanation += "Or\n";
			//			ShapeOr orshape = (ShapeOr)expr;
			//			for(ShapeExpr subexp : orshape.getSubExpressions()) {
			//				explanation += explainShape(subexp, explanation);
			//			}
		}else if(expr instanceof ShapeExprRef) {
			//not in the rdf model - this is a match of this expr on a shape
			//e.g. <http://purl.obolibrary.org/obo/go/shapes/GoCamEntity>
			//explanation += "\t\tis a: "+((ShapeExprRef) expr).getLabel()+"\n";			
		}else if(expr instanceof Shape) {
			Shape shape = (Shape)expr;
			TripleExpr texp = shape.getTripleExpression();
			prop_range = getPropertyRangeMap(texp, prop_range);
		}else if (expr instanceof NodeConstraint) {
			//NodeConstraint nc = (NodeConstraint)expr;
			//explanation += "\t\tnode constraint "+nc.toPrettyString();
		}
		else {
			//explanation+=" Not sure what is: "+expr;
		}

		return prop_range;
	}



	public static Map<String, Set<String>> getPropertyRangeMap(TripleExpr texp, Map<String, Set<String>> prop_range) {
		if(prop_range==null) {
			prop_range = new HashMap<String, Set<String>>();
		}

		if(texp instanceof TripleConstraint) {
			TripleConstraint tcon = (TripleConstraint)texp;
			TCProperty tprop = tcon.getProperty();
			ShapeExpr range = tcon.getShapeExpr();
			String prop_uri = tprop.getIri().toString();
			Set<String> ranges = prop_range.get(prop_uri);
			if(ranges==null) {
				ranges = new HashSet<String>();
			}
			ranges.addAll(getShapeExprRefs(range,ranges));
			prop_range.put(prop_uri, ranges);
		}else if(texp instanceof EachOf){
			EachOf each = (EachOf)texp;
			for(TripleExpr eachtexp : each.getSubExpressions()) {
				if(!texp.equals(eachtexp)) {
					prop_range = getPropertyRangeMap(eachtexp, prop_range);
				}
			}
		}else if(texp instanceof RepeatedTripleExpression) {
			RepeatedTripleExpression rep = (RepeatedTripleExpression)texp;
			rep.getCardinality().toString();
			TripleExpr t = rep.getSubExpression();
			prop_range = getPropertyRangeMap(t, prop_range);
		}
		else {
			System.out.println("\tlost again here on "+texp);
		}
		return prop_range;
	}

	private static Set<String> getShapeExprRefs(ShapeExpr expr, Set<String> shape_refs) {
		if(shape_refs==null) {
			shape_refs = new HashSet<String>();
		}
		if(expr instanceof ShapeExprRef) {
			shape_refs.add(((ShapeExprRef) expr).getLabel().stringValue());
		}else if(expr instanceof ShapeAnd) {
			ShapeAnd andshape = (ShapeAnd)expr;
			//			for(ShapeExpr subexp : andshape.getSubExpressions()) {
			//				shape_refs = getShapeExprRefs(subexp, shape_refs);
			//			}
		}else if (expr instanceof ShapeOr) {
			ShapeOr orshape = (ShapeOr)expr;
			for(ShapeExpr subexp : orshape.getSubExpressions()) {
				shape_refs = getShapeExprRefs(subexp, shape_refs);
			}
		}else {
			System.out.println("currently ignoring "+expr);
		}
		return shape_refs;
	}
	
	public static String getModelTitle(Model model) {
		String model_title = null;
		String q = "select ?cam ?title where {"
				+ "?cam <http://purl.org/dc/elements/1.1/title> ?title }";
		//	+ "?cam <"+DC.description.getURI()+"> ?title }";
		QueryExecution qe = QueryExecutionFactory.create(q, model);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource model_id_resource = qs.getResource("cam");
			Literal title = qs.getLiteral("title");
			model_title = title.getString();
		}
		qe.close();
		return model_title;
	}
}
