/**
 * 
 */
package org.geneontology.minerva.validation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.apache.log4j.Logger;
import org.geneontology.minerva.curie.CurieHandler;
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
import fr.inria.lille.shexjava.schema.abstrsynt.TripleExprRef;
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
	private static final Logger LOGGER = Logger.getLogger(ShexValidator.class);
	public ShexSchema schema;
	public Map<String, String> GoQueryMap;
	public OWLReasoner tbox_reasoner;
	public static final String endpoint = "http://rdf.geneontology.org/blazegraph/sparql";
	public Map<Label, Map<String, Set<String>>> shape_expected_property_ranges;
	public CurieHandler curieHandler;
	public RDF rdfFactory;

	/**
	 * @throws Exception 
	 * 
	 */
	public ShexValidator(String shexpath, String goshapemappath, OWLReasoner tbox_reasoner_, CurieHandler curieHandler_) throws Exception {
		init(new File(shexpath), new File(goshapemappath), tbox_reasoner_, curieHandler_);
	}

	public ShexValidator(File shex_schema_file, File shex_map_file, OWLReasoner tbox_reasoner_, CurieHandler curieHandler_) throws Exception {
		init(shex_schema_file, shex_map_file, tbox_reasoner_, curieHandler_);
	}

	public void init(File shex_schema_file, File shex_map_file, OWLReasoner tbox_reasoner_, CurieHandler curieHandler_) throws Exception {
		schema = GenParser.parseSchema(shex_schema_file.toPath());
		GoQueryMap = makeGoQueryMap(shex_map_file.getAbsolutePath());
		tbox_reasoner = tbox_reasoner_;
		shape_expected_property_ranges = new HashMap<Label, Map<String, Set<String>>>();
		curieHandler = curieHandler_;
		rdfFactory = new SimpleRDF();
		for(String shapelabel : GoQueryMap.keySet()) {
			if(shapelabel.equals("http://purl.obolibrary.org/obo/go/shapes/AnnotatedEdge")) {
				continue;
			}
			Label shape_label = new Label(rdfFactory.createIRI(shapelabel));
			ShapeExpr rule = schema.getRules().get(shape_label);
			Map<String, Set<String>> expected_property_ranges = getPropertyRangeMap(rule, null);
			shape_expected_property_ranges.put(shape_label, expected_property_ranges);
		}
		LOGGER.info("shex validator ready");
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
		ShexValidationReport r = new ShexValidationReport(null, test_model);	

		RDF rdfFactory = new SimpleRDF();
		JenaRDF jr = new JenaRDF();
		//this shex implementation likes to use the commons JenaRDF interface, nothing exciting here
		JenaGraph shexy_graph = jr.asGraph(test_model);
		//recursive only checks the focus node against the chosen shape.  
		RecursiveValidation shex_model_validator = new RecursiveValidation(schema, shexy_graph);
		//for each shape in the query map (e.g. MF, BP, CC, etc.)
		boolean all_good = true;
		Map<Resource, Set<String>> node_shapes = new HashMap<Resource, Set<String>>();
		for(String shapelabel : GoQueryMap.keySet()) {
			//not quite the same pattern as the other shapes
			//TODO needs more work 
			if(shapelabel.equals("http://purl.obolibrary.org/obo/go/shapes/AnnotatedEdge")) {
				continue;
			}
			//get the nodes in this model that SHOULD match the shape
			Set<Resource> focus_nodes = getFocusNodesBySparql(test_model, GoQueryMap.get(shapelabel));
			//shape_nodes.put(shapelabel, focus_nodes);

			for(Resource focus_node : focus_nodes) {
				Set<String> shapes = node_shapes.get(focus_node);
				if(shapes==null) {
					shapes = new HashSet<String>();
				}
				shapes.add(shapelabel);
				node_shapes.put(focus_node, shapes);
			}
		}
		//prune down evaluation to only consider most specific of matched shapes for a given resource
		//TODO - do this once up front.. massively redundant waste in here right now.  
		Map<Resource, Set<String>> node_s_shapes = new HashMap<Resource, Set<String>>();
		for(Resource node : node_shapes.keySet()) {
			Set<String> shapes = node_shapes.get(node);
			Set<String> shapes_to_remove = new HashSet<String>();
			for(String shape1 : shapes) {
				Set<Resource> shape1_nodes = getFocusNodesBySparql(test_model, GoQueryMap.get(shape1));
				for(String shape2 : shapes) {
					if(shape1.equals(shape2)) {
						continue;
					}
					Set<Resource> shape2_nodes = getFocusNodesBySparql(test_model, GoQueryMap.get(shape2));
					//if shape1 contains all of shape2 - e.g. mf would contain all transporter activity
					if(shape1_nodes.containsAll(shape2_nodes)) {
						//then remove shape1 from this resource (as shape2 is more specific). 
						shapes_to_remove.add(shape1);
					}
				}
			}
			shapes.removeAll(shapes_to_remove);
			node_s_shapes.put(node, shapes);
		}

		for(Resource focus_node_resource : node_s_shapes.keySet()) {
			Set<String> shape_nodes = node_s_shapes.get(focus_node_resource);
			LOGGER.info("validating "+focus_node_resource+" "+shape_nodes);

			for(String shapelabel : shape_nodes) {
				Label shape_label = new Label(rdfFactory.createIRI(shapelabel));
				if(focus_node_resource==null) {
					System.out.println("null focus node for shape "+shape_label);
					continue;
				}
				//check for use of properties not defined for this shape (okay if OPEN, not if CLOSED)
				Set<ShexViolation> extra_prop_violations = checkForExtraProperties(focus_node_resource, test_model, shape_label, shex_model_validator);
				if(extra_prop_violations != null && !extra_prop_violations.isEmpty()) {
					for(Violation v : extra_prop_violations) {
						r.addViolation(v);
					}				
					all_good = false;
				}
				//run the validation on the node if possible..
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
				node = getCurie(focus_node_id);
				int timeout = 10;
				Typing typing = validateNodeWithTimeout(shex_model_validator, focus_node_resource, shape_label, timeout);
				if(typing!=null) {
					Status status = typing.getStatus(focus_node, shape_label);
					if(status.equals(Status.CONFORMANT)) {
						Set<String> shape_ids = r.node_matched_shapes.get(node);
						if(shape_ids==null) {
							shape_ids = new HashSet<String>();
						}
						shape_ids.add(shapelabel);				
						r.node_matched_shapes.put(node, shape_ids);
					}else if(status.equals(Status.NONCONFORMANT)) {
						all_good = false;
						//implementing a start on a generic violation report structure here
						ShexViolation violation = new ShexViolation(node);				 					
						ShexExplanation explanation = new ShexExplanation();
						String shape_curie = getCurie(shapelabel);
						explanation.setShape(shape_curie);				
						Set<ShexConstraint> unmet_constraints = getUnmetConstraints(focus_node_resource, shapelabel, test_model, timeout);				
						if(unmet_constraints!=null) {
							for(ShexConstraint constraint : unmet_constraints) {
								explanation.addConstraint(constraint);
								violation.addExplanation(explanation);
							}	
						}else {
							explanation.setErrorMessage("explanation computation timed out");
							violation.addExplanation(explanation);
						}
						r.addViolation(violation);			
					}else if(status.equals(Status.NOTCOMPUTED)) {
						//if any of these are not computed, there is a problem
						String error = focus_node_id+" was not tested against "+shapelabel;
						LOGGER.error(error);
					}
				}else {
					LOGGER.info("typing failed");
					all_good = false;
					ShexViolation violation = new ShexViolation(node);				 					
					ShexExplanation explanation = new ShexExplanation();
					explanation.setErrorMessage("Validating this node was canceled because it took more then "+timeout+" seconds");
					String shape_curie = getCurie(shapelabel);
					explanation.setShape(shape_curie);				
					violation.addExplanation(explanation);
					r.addViolation(violation);	
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


	public Typing validateNodeWithTimeout(RecursiveValidation shex_model_validator, Resource focus_node_resource, Label shape_label, int timeout) {
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
		node = getCurie(focus_node_id);
		//this can take a while - give up if it gets stuck
		//limit total time to avoid service death on some weird edge case
		final ExecutorService service = Executors.newSingleThreadExecutor();
		final RDFTerm test_node = focus_node;
		try {
			final Future<?> f = service.submit(() -> {
				shex_model_validator.validate(test_node, shape_label);						
			});
			if(null==f.get(timeout, TimeUnit.SECONDS)) {
				//capture the result
				Typing typing = shex_model_validator.getTyping();
				return typing;
			}

		} catch (final TimeoutException e) {
			LOGGER.error("shex validation took to long for "+focus_node_resource);
			return null;		
		} catch (InterruptedException e) {
			LOGGER.error("And we have an interrupted exception:");
			e.printStackTrace();
			return null;
		} catch (ExecutionException e) {
			LOGGER.error("And we have an execution exception:");
			e.printStackTrace();
			return null;
		}  finally {
			service.shutdown();			
		}
		return null;
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

	/**
	 * Check each focus node for the use of any properties that don't appear in the shape
	 * @param focus_nodes
	 * @param model
	 * @param shape_label
	 * @return
	 */
	public Set<ShexViolation> checkForExtraProperties(Resource node_r, Model model, Label shape_label, RecursiveValidation shex_model_validator){
		Set<ShexViolation> violations = new HashSet<ShexViolation>();
		Set<String> allowed_properties = this.shape_expected_property_ranges.get(shape_label).keySet();
		Set<String> actual_properties = new HashSet<String>();
		Map<String, RDFNode> prop_value = new HashMap<String, RDFNode>(); //don't really care if there are multiple values, one will do. 
		String sparql = "select distinct ?prop ?value where{ <"+node_r.getURI()+"> ?prop ?value }";		
		QueryExecution qe = QueryExecutionFactory.create(sparql, model);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource prop = qs.getResource("prop");
			RDFNode value = qs.get("value");
			actual_properties.add(prop.getURI());
			prop_value.put(prop.getURI(), value);
		}
		qe.close();
		actual_properties.removeAll(allowed_properties);
		if(!actual_properties.isEmpty()) {
			ShexViolation extra = new ShexViolation(getCurie(node_r.getURI()));
			Set<ShexExplanation> explanations = new HashSet<ShexExplanation>();
			for(String prop : actual_properties) {
				String value = "value";
				boolean value_is_uri = false;
				if(prop_value.get(prop).isResource()) {
					value = prop_value.get(prop).asResource().getURI();
					value_is_uri = true;
				}else if(prop_value.get(prop).isLiteral()) {
					value = prop_value.get(prop).asLiteral().getString();
				}
				ShexExplanation extra_explain = new ShexExplanation();
				extra_explain.setShape(getCurie(shape_label.stringValue()));
				Set<String> intended_range_shapes = new HashSet<String>();
				//For this CLOSED test, no shape fits in intended.  Any use of the property here would be incorrect.
				intended_range_shapes.add("owl:Nothing");
				Set<String> node_types = getNodeTypes(model, node_r.getURI());
				Set<String> object_types = null;
				//TODO consider here.  extra info but not really meaningful - anything in the range would be wrong. 
				Set<String> matched_range_shapes = null;
				if(value_is_uri) {
					object_types = getNodeTypes(model, value);
					matched_range_shapes  = getAllMatchedShapes(value, shex_model_validator);
				}				
				String report_prop = getCurie(prop);
				ShexConstraint c = new ShexConstraint(value, report_prop, intended_range_shapes, node_types, object_types);
				c.setMatched_range_shapes(matched_range_shapes);
				Set<ShexConstraint> cs = new HashSet<ShexConstraint>();
				cs.add(c);
				extra_explain.setConstraints(cs);
				explanations.add(extra_explain);
			}
			extra.setExplanations(explanations);			
			violations.add(extra);
		}
		return violations;

	}

	public Model enrichSuperClasses(Model model) {
		LOGGER.info("model size before reasoner expansion: "+model.size());
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
		LOGGER.info("model size after reasoner expansion: "+model.size());
		return model;
	}

	public Set<String> getNodeTypes(Model model, String node_uri) {

		String getOntTerms = 
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
						+ "PREFIX owl: <http://www.w3.org/2002/07/owl#> "
						+ "SELECT DISTINCT ?type " + 
						"        WHERE { " + 
						"<"+node_uri+"> rdf:type ?type . " + 
						"FILTER(?type != owl:NamedIndividual)" + 
						"        }";
		Set<String> types = new HashSet<String>();
		try{
			QueryExecution qe = QueryExecutionFactory.create(getOntTerms, model);
			ResultSet results = qe.execSelect();
			while (results.hasNext()) {
				QuerySolution qs = results.next();
				Resource type = qs.getResource("type");
				types.add(getCurie(type.getURI()));
				OWLClass t = tbox_reasoner.getRootOntology().getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(type.getURI()));
				for(OWLClass p : tbox_reasoner.getSuperClasses(t, false).getFlattened()) {
					String type_curie = getCurie(p.getIRI().toString());
					types.add(type_curie);
				}
			}
			qe.close();
		} catch(QueryParseException e){
			LOGGER.error(getOntTerms);
			e.printStackTrace();
		}

		return types;
	}

	private Set<ShexConstraint> getUnmetConstraints(Resource focus_node, String shape_id, Model model, int timeout) {
		final ExecutorService service = Executors.newSingleThreadExecutor();
		try {
			Set<ShexConstraint> unmet_constraints = new HashSet<ShexConstraint>();
			final Future<?> f = service.submit(() -> {
				Set<String> node_types = getNodeTypes(model, focus_node.getURI());		
				
				RDF rdfFactory = new SimpleRDF();
				Label shape_label = new Label(rdfFactory.createIRI(shape_id));
				Map<String, Set<String>> expected_property_ranges = shape_expected_property_ranges.get(shape_label);
				//get a map from properties to actual shapes of the asserted objects
				JenaRDF jr = new JenaRDF();
				JenaGraph shexy_graph = jr.asGraph(model); 
				RecursiveValidation shex_model_validator = new RecursiveValidation(schema, shexy_graph);

				//get the focus node in the rdf model
				//check for assertions with properties in the target shape
				for(String prop_uri : expected_property_ranges.keySet()) {
					Property prop = model.getProperty(prop_uri);
					//checking on objects of this property for the problem node.
					for (StmtIterator i = focus_node.listProperties(prop); i.hasNext(); ) {
						RDFNode obj = i.nextStatement().getObject();
						//check the computed shapes for this individual
						if(!obj.isResource()) {
							continue;
							//no checks on literal values at this time
						}else if(prop_uri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")&&obj.asResource().getURI().equals("http://www.w3.org/2002/07/owl#NamedIndividual")) {
							continue; //ignore type owl individual
						}
						RDFTerm range_obj = rdfFactory.createIRI(obj.asResource().getURI());
						//does it hit any allowable shapes?
						boolean good = false;
						//TODO many property ranges are MISSING from previous step
						//e.g. any OR will not show up here.  
						Set<String> expected_ranges = expected_property_ranges.get(prop_uri);
						for(String target_shape_uri : expected_ranges) {
							if(target_shape_uri.equals(".")) {
								//anything is fine 
								good = true;
								break;
							}else if(target_shape_uri.trim().equals("<http://www.w3.org/2001/XMLSchema#string>")) {
								//ignore syntax type checking for now
								good = true;
								break;
							}
							Label target_shape_label = null;
							try {

								target_shape_label = new Label(rdfFactory.createIRI(target_shape_uri));

								shex_model_validator.validate(range_obj, target_shape_label);
								//could use refine to get all of the actual shapes - but would want to do this
								//once per validation...
								//RefineValidation shex_refine_validator = new RefineValidation(schema, shexy_graph);
								Typing shape_test = shex_model_validator.getTyping();
								Pair<RDFTerm, Label> p = new Pair<RDFTerm, Label>(range_obj, target_shape_label);
								Status r = shape_test.getStatusMap().get(p);
								if(r.equals(Status.CONFORMANT)) {
									good = true;
									break;
								}
							}catch(Exception e) {
								System.out.println(e+"\nbroken target shape uri:"+target_shape_uri);
							}
						}
						if(!good) {					
							if(obj.isURIResource()) {
								String object = obj.toString();
								Set<String> object_types = getNodeTypes(model, obj.toString());

								String property = prop.toString();
								object = getCurie(object);
								property = getCurie(property);
								Set<String> expected = new HashSet<String>();
								for(String e : expected_property_ranges.get(prop_uri)) {
									String curie_e = getCurie(e);
									expected.add(curie_e);
								}					
								ShexConstraint constraint = new ShexConstraint(object, property, expected, node_types, object_types);
								//return all shapes that are matched by this node for explanation
								Set<String> obj_matched_shapes = getAllMatchedShapes(range_obj, shex_model_validator);
								constraint.setMatched_range_shapes(obj_matched_shapes);
								unmet_constraints.add(constraint);
							}else {
								ShexConstraint constraint = new ShexConstraint(obj.toString(), getCurie(prop.toString()), null, node_types, null);
								//return all shapes that are matched by this node for explanation
								Set<String> obj_matched_shapes = getAllMatchedShapes(range_obj, shex_model_validator);
								constraint.setMatched_range_shapes(obj_matched_shapes);
								unmet_constraints.add(constraint);
							}
						}
					}
				}
			});
			if(null==f.get(timeout, TimeUnit.SECONDS)) {
				//capture the result
				return unmet_constraints;
			}
		} catch (final TimeoutException e) {
			LOGGER.error("shex explanation took to long for "+focus_node);
			return null;		
		} catch (InterruptedException e) {
			LOGGER.error("And we have an interrupted exception:");
			e.printStackTrace();
			return null;
		} catch (ExecutionException e) {
			LOGGER.error("And we have an execution exception:");
			e.printStackTrace();
			return null;
		}  finally {
			service.shutdown();		}

		return null;
	}

	public Set<String> getAllMatchedShapes(RDFTerm node, RecursiveValidation shex_model_validator){
		Set<Label> all_shapes_in_schema = getAllShapesInSchema();
		Set<String> obj_matched_shapes = new HashSet<String>();
		for(Label target_shape_label : all_shapes_in_schema) {
			shex_model_validator.validate(node, target_shape_label);
			Typing shape_test = shex_model_validator.getTyping();
			Pair<RDFTerm, Label> p = new Pair<RDFTerm, Label>(node, target_shape_label);
			Status r = shape_test.getStatusMap().get(p);
			if(r.equals(Status.CONFORMANT)) {
				obj_matched_shapes.add(getCurie(target_shape_label.stringValue()));
			}
		}
		return obj_matched_shapes;
	}

	public Set<String> getAllMatchedShapes(String node_uri, RecursiveValidation shex_model_validator){
		RDFTerm range_obj = rdfFactory.createIRI(node_uri);
		return getAllMatchedShapes(range_obj, shex_model_validator);
	}


	private Set<Label> getAllShapesInSchema() {		
		return schema.getRules().keySet();
	}


	public String getCurie(String uri) {
		String curie = uri;
		if(curieHandler!=null) {
			curie = curieHandler.getCuri(IRI.create(uri));
		}
		return curie;
	}


	public Map<String, Set<String>> getPropertyRangeMap(ShapeExpr expr, Map<String, Set<String>> prop_range){
		if(prop_range==null) {
			prop_range = new HashMap<String, Set<String>>();
		}
		String explanation = "";
		if(expr instanceof ShapeAnd) {
			explanation += "And\n";
			ShapeAnd andshape = (ShapeAnd)expr;
			for(ShapeExpr subexp : andshape.getSubExpressions()) {
				//boolean is_closed = subexp.closed;
				prop_range = getPropertyRangeMap(subexp, prop_range);
				explanation += subexp+" ";
			}
		}
		else if (expr instanceof ShapeOr) {
			explanation += "Or\n";
			ShapeOr orshape = (ShapeOr)expr;
			for(ShapeExpr subexp : orshape.getSubExpressions()) {
				explanation += subexp;
				//explainShape(subexp, explanation);
			}
		}else if(expr instanceof ShapeExprRef) {
			ShapeExprRef ref = (ShapeExprRef) expr; 
			ShapeExpr ref_expr = ref.getShapeDefinition();
			prop_range = getPropertyRangeMap(ref_expr, prop_range);
			//not in the rdf model - this is a match of this expr on a shape
			//e.g. <http://purl.obolibrary.org/obo/go/shapes/GoCamEntity>
			explanation += "\t\tis a: "+((ShapeExprRef) expr).getLabel()+"\n";			
		}else if(expr instanceof Shape) {
			Shape shape = (Shape)expr;
			TripleExpr texp = shape.getTripleExpression();
			prop_range = getPropertyRangeMap(texp, prop_range);
		}else if (expr instanceof NodeConstraint) {
			NodeConstraint nc = (NodeConstraint)expr;
			explanation += "\t\tnode constraint "+nc.toPrettyString();
		}
		else {
			explanation+=" Not sure what is: "+expr;
		}
		return prop_range;
	}



	public Map<String, Set<String>> getPropertyRangeMap(TripleExpr texp, Map<String, Set<String>> prop_range) {
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
		}else if(texp instanceof TripleExprRef) {
			TripleExprRef ref = (TripleExprRef)texp;
			prop_range = getPropertyRangeMap(ref.getTripleExp(), prop_range);
		}
		else {
			System.out.println("\tlost again here on "+texp);
		}
		return prop_range;
	}

	private Set<String> getShapeExprRefs(ShapeExpr expr, Set<String> shape_refs) {
		if(shape_refs==null) {
			shape_refs = new HashSet<String>();
		}
		if(expr instanceof ShapeExprRef) {
			shape_refs.add(((ShapeExprRef) expr).getLabel().stringValue());
		}else if(expr instanceof ShapeAnd) {
			ShapeAnd andshape = (ShapeAnd)expr;
			System.out.println("currently ignoring And shape in range: "+expr);
			//			for(ShapeExpr subexp : andshape.getSubExpressions()) {
			//				shape_refs = getShapeExprRefs(subexp, shape_refs);
			//			}
		}else if (expr instanceof ShapeOr) {
			ShapeOr orshape = (ShapeOr)expr;
			for(ShapeExpr subexp : orshape.getSubExpressions()) {
				shape_refs = getShapeExprRefs(subexp, shape_refs);
			}
		}else if (expr instanceof NodeConstraint){
			String reference_string = ((NodeConstraint) expr).toPrettyString();
			reference_string = reference_string.replace("<", "");
			reference_string = reference_string.replace(">", "");
			reference_string = reference_string.replace("[", "");
			reference_string = reference_string.replace("]", "");
			reference_string = reference_string.trim();
			shape_refs.add(reference_string);
		}else {
			System.out.println("currently ignoring "+expr);
		}
		return shape_refs;
	}

	public static String getModelTitle(Model model) {
		String model_title = null;
		String q = "select ?cam ?title where {"
				+ "GRAPH ?cam {  "
				+ "?cam <http://purl.org/dc/elements/1.1/title> ?title"
				+ "}}";
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
