/**
 * 
 */
package org.geneontology.minerva.server.inferences;

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
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.geneontology.minerva.server.inferences.MapInferenceProvider.ShexValidationResult;

import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.parsing.GenParser;
import fr.inria.lille.shexjava.util.Pair;
import fr.inria.lille.shexjava.validation.RecursiveValidation;
import fr.inria.lille.shexjava.validation.Status;
import fr.inria.lille.shexjava.validation.Typing;

/**
 * @author bgood
 *
 */
public class ShexController {
	public ShexSchema schema;
	public Map<String, String> GoQueryMap;
	/**
	 * @throws Exception 
	 * 
	 */
	public ShexController(String shexpath, String goshapemappath) throws Exception {
		schema = GenParser.parseSchema(new File(shexpath).toPath());
		GoQueryMap = makeGoQueryMap(goshapemappath);
	}

	public ShexController(File shex_schema_file, File shex_map_file) throws Exception {
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
	
	public ShexValidationResult runShapeMapValidation(Model test_model, boolean stream_output) throws Exception {
		Map<String, Typing> shape_node_typing = validateGoShapeMap(schema, test_model, GoQueryMap);
		ShexValidationResult r = new ShexValidationResult(test_model);
		RDF rdfFactory = new SimpleRDF();
		for(String shape_node : shape_node_typing.keySet()) {
			Typing typing = shape_node_typing.get(shape_node);
			String shape_id = shape_node.split(",")[0];
			String focus_node_iri = shape_node.split(",")[1];
			String result = getValidationReport(typing, rdfFactory, focus_node_iri, shape_id, true);
			//TODO refactor
			Label shape_label = new Label(rdfFactory.createIRI(shape_id));
			RDFTerm focus_node = rdfFactory.createIRI(focus_node_iri);
			Pair<RDFTerm, Label> p = new Pair<RDFTerm, Label>(focus_node, shape_label);
			Status node_r = typing.getStatusMap().get(p);
			if(node_r.equals(Status.NONCONFORMANT)) {
				r.node_is_valid.put(focus_node_iri, false);
			}else {
				r.node_is_valid.put(focus_node_iri, true);
			}
			r.model_report+=result;
		}
		if(r.model_report.contains("NONCONFORMANT")) {
			r.model_is_valid=false;
			if(stream_output) {
				System.out.println("Invalid model: GO shape map report for model:"+r.model_title+"\n"+r.model_report);
			}
		}else {
			r.model_is_valid=true;
			if(stream_output) {
				System.out.println("Valid model:"+r.model_title);
			}
		}
		return r;
	}

	public static String getValidationReport(Typing typing_result, RDF rdfFactory, String focus_node_iri, String shape_id, boolean only_negative) throws Exception {
		Label shape_label = new Label(rdfFactory.createIRI(shape_id));
		RDFTerm focus_node = rdfFactory.createIRI(focus_node_iri);
		Pair<RDFTerm, Label> p = new Pair<RDFTerm, Label>(focus_node, shape_label);
		Status r = typing_result.getStatusMap().get(p);
		String s = "";
		if(r!=null) {
			if(only_negative) {
				if(r.equals(Status.NONCONFORMANT)) {
					s+=p.two+"\t"+p.one+"\t"+r.toString()+"\n";
				}
			}else { //report all
				s+=p.two+"\t"+p.one+"\t"+r.toString()+"\n";
			}
		}
		return s;
	}
	
	public static Map<String, Typing> validateGoShapeMap(ShexSchema schema, Model jena_model, Map<String, String> GoQueryMap) throws Exception {
		Map<String, Typing> shape_node_typing = new HashMap<String, Typing>();
		Typing result = null;
		RDF rdfFactory = new SimpleRDF();
		JenaRDF jr = new JenaRDF();
		//this shex implementation likes to use the commons JenaRDF interface, nothing exciting here
		JenaGraph shexy_graph = jr.asGraph(jena_model);
		//recursive only checks the focus node against the chosen shape.  
		RecursiveValidation shex_recursive_validator = new RecursiveValidation(schema, shexy_graph);
		for(String shapelabel : GoQueryMap.keySet()) {
			Label shape_label = new Label(rdfFactory.createIRI(shapelabel));
			Set<String> focus_nodes = getFocusNodesBySparql(jena_model, GoQueryMap.get(shapelabel));
			for(String focus_node_iri : focus_nodes) {
				RDFTerm focus_node = rdfFactory.createIRI(focus_node_iri);
				shex_recursive_validator.validate(focus_node, shape_label);
				result = shex_recursive_validator.getTyping();
				shape_node_typing.put(shapelabel+","+focus_node_iri, result);
			}
		}
		return shape_node_typing;
	}

	public static Set<String> getFocusNodesBySparql(Model model, String sparql){
		Set<String> nodes = new HashSet<String>();
		QueryExecution qe = QueryExecutionFactory.create(sparql, model);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource node = qs.getResource("x");
			nodes.add(node.getURI());
		}
		qe.close();
		return nodes;
	}
}
