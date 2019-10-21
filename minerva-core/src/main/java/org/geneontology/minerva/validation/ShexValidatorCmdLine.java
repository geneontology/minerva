/**
 * 
 */
package org.geneontology.minerva.validation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.abstrsynt.Annotation;
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
import fr.inria.lille.shexjava.validation.RefineValidation;
import fr.inria.lille.shexjava.validation.Status;
import fr.inria.lille.shexjava.validation.Typing;

/**
 * @author bgood
 *
 */
public class ShexValidatorCmdLine {
	public ShexValidator validator;
	/**
	 * 
	 */
	public ShexValidatorCmdLine() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {	
		String url_for_tbox = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
		ShexValidator validator = null;
		String shexpath = null;//"../shapes/go-cam-shapes.shex";
		String model_file = "";//"../test_ttl/go_cams/should_pass/typed_reactome-homosapiens-Acetylation.ttl";
		boolean addSuperClasses = false;
		boolean addSuperClassesLocal = false;
		String extra_endpoint = null;
		Map<String, Model> name_model = new HashMap<String, Model>();
		// create Options object
		Options options = new Options();
		options.addOption("f", true, "ttl file or directory of ttl files to validate");
		options.addOption("s", true, "shex schema file");
		options.addOption("m", true, "query shape map file"); 
		options.addOption("elocal", false, "if added, will use download and use http://purl.obolibrary.org/obo/go/extensions/go-lego.owl to add subclass relations to the model");
		options.addOption("e", false, "if added, will use rdf.geneontology.org to add subclass relations to the model");
		options.addOption("extra_endpoint", true, "if added, will use the additional endpoint at the indicated url - "
				+ "e.g. http://192.168.1.5:9999/blazegraph/sparql to provide additional suuperclass expansions.  "
				+ "Use this when the main GO endpoint rdf.geneontology.org does not contain all of the information required.");
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse( options, args);

		if(cmd.hasOption("f")) {
			model_file = cmd.getOptionValue("f");
			//accepts both single files and directories
			name_model = Enricher.loadRDF(model_file);
		}
		else {
			System.out.println("please provide a file to validate.  e.g. -f ../../test_ttl/go_cams/should_pass/typed_reactome-homosapiens-Acetylation.ttl");
			System.exit(0);
		}
		if(cmd.hasOption("s")) {
			shexpath = cmd.getOptionValue("s");
		}
		else {
			System.out.println("please provide a shex schema file to validate.  e.g. -s ../../shapes/go-cam-shapes.shex");
			System.exit(0);
		}		
		if(cmd.hasOption("m")) { 
			String shapemappath = cmd.getOptionValue("m");
			validator = new ShexValidator(shexpath, shapemappath);
		}
		else {
			System.out.println("please provide a shape map file to validate.  e.g. -s ../../shapes/go-cam-shapes.shapemap");
			System.exit(0);
		}		
		if(cmd.hasOption("e")) {
			addSuperClasses = true;
		}else if(cmd.hasOption("elocal")) {
			addSuperClassesLocal = true;
		}
		if(cmd.hasOption("extra_endpoint")) {
			extra_endpoint = cmd.getOptionValue("extra_endpoint");
		}
//		boolean run_all = false;
//		if(cmd.hasOption("all")) {
//			run_all = true;
//		}

		FileWriter w = new FileWriter("report_file.txt");
		int good = 0; int bad = 0;
		Enricher enrich = new Enricher(extra_endpoint, null);
		if(addSuperClassesLocal) {
			URL tbox_location = new URL(url_for_tbox);
			File tbox_file = new File("./target/go-lego.owl");
			System.out.println("downloading tbox ontology from "+url_for_tbox);
			org.apache.commons.io.FileUtils.copyURLToFile(tbox_location, tbox_file);
			System.out.println("loading tbox ontology from "+tbox_file.getAbsolutePath());
			OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();					
			OWLOntology tbox = ontman.loadOntologyFromOntologyDocument(tbox_file);
			System.out.println("done loading, building structural reasoner");
			OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
			OWLReasoner tbox_reasoner = reasonerFactory.createReasoner(tbox);
			enrich = new Enricher(null, tbox_reasoner);
		}
		for(String name : name_model.keySet()) {
			Model test_model = name_model.get(name);
			if(addSuperClasses||addSuperClassesLocal) {
				test_model = enrich.enrichSuperClasses(test_model);
			}
			if(validator.GoQueryMap!=null){
				boolean stream_output = true;
				ShexValidationReport r = validator.runShapeMapValidation(test_model, stream_output);
				System.out.println(r.getAsText());
				w.write(name+"\t");
				if(!r.conformant) {
					w.write("invalid\n");
					bad++;
				}else {
					good++;
					w.write("valid\n");
				}
			}
		}
		w.close();
		System.out.println("input: "+model_file+" total:"+name_model.size()+" Good:"+good+" Bad:"+bad);
	}


	public static String explainShape(ShapeExpr expr, String explanation) {
		if(expr instanceof ShapeAnd) {
			explanation += "And\n";
			ShapeAnd andshape = (ShapeAnd)expr;
			for(ShapeExpr subexp : andshape.getSubExpressions()) {
				explanation += explainShape(subexp, explanation);
			}
		}else if(expr instanceof ShapeExprRef) {
			explanation += "\t\tis a: "+((ShapeExprRef) expr).getLabel()+"\n";
		}else if(expr instanceof Shape) {
			Shape shape = (Shape)expr;
			TripleExpr texp = shape.getTripleExpression();
			explanation += "\n\t\t"+explainTripleExpression(texp, "");
		}else if (expr instanceof NodeConstraint) {
			NodeConstraint nc = (NodeConstraint)expr;
			explanation += "\t\tnode constraint "+nc.toPrettyString();
		}else if (expr instanceof ShapeOr) {
			explanation += "Or\n";
			ShapeOr orshape = (ShapeOr)expr;
			for(ShapeExpr subexp : orshape.getSubExpressions()) {
				explanation += explainShape(subexp, explanation);
			}
		}
		else {
			explanation+=" Not sure what is: "+expr;
		}
		return explanation;
	}

	public static String explainTripleExpression(TripleExpr texp, String explanation) {
		if(texp instanceof TripleConstraint) {
			TripleConstraint tcon = (TripleConstraint)texp;
			TCProperty tprop = tcon.getProperty();
			ShapeExpr range = tcon.getShapeExpr();
			explanation +="\t\trestricts property: "+tprop.toPrettyString()+"\t"+explainShape(range, "");
		}else if(texp instanceof EachOf){
			EachOf each = (EachOf)texp;
			explanation +="\n\t\tEach of:";
			for(TripleExpr eachtexp : each.getSubExpressions()) {
				if(!texp.equals(eachtexp)) {
					explanation += explainTripleExpression(eachtexp, "");
				}
			}
		}else if(texp instanceof RepeatedTripleExpression) {
			RepeatedTripleExpression rep = (RepeatedTripleExpression)texp;
			//rep.getCardinality().toString();
			explanation += explainTripleExpression(rep.getSubExpression(), explanation);	
		}
		else {
			explanation +="\tlost again here";
		}
		return explanation;
	}


	

	public static void printSchemaProperties(ShexSchema schema) {
		Map<Label, ShapeExpr> rulelabel_shapeexpr = schema.getRules();
		for(Label label : rulelabel_shapeexpr.keySet()) {
			if(label.toPrettyString().equals("<http://purl.obolibrary.org/obo/go/shapes/MolecularFunction>")) {
				System.out.println("shape label: "+label.toPrettyString());
				ShapeExpr exp = rulelabel_shapeexpr.get(label);
				System.out.println("shape expr full: "+exp.toPrettyString());
				System.out.println("\tExplanation of shape:\n\t"+explainShape(exp,""));
			}

		}
	}



	public static void printSchemaComments(ShexSchema schema) {

		for(Label label : schema.getRules().keySet()) {

			ShapeExpr shape_exp = schema.getRules().get(label);
			Shape shape_rule = (Shape) schema.getRules().get(label);

			List<Annotation> annotations = shape_rule.getAnnotations();
			for(Annotation a : annotations) {
				System.out.println(shape_rule.getId()+" \n\t"+a.getPredicate()+" "+a.getObjectValue());
			}
			//would need to make the following recursive to be complete.
			TripleExpr trp = shape_rule.getTripleExpression();
			Set<Annotation> sub_annotations = getAnnos(null, trp);
			for(Annotation a : sub_annotations) {
				System.out.println(shape_rule.getId()+"SUB \n\t"+a.getPredicate()+" "+a.getObjectValue());
			}
		}
	}

	public static Set<Annotation> getAnnos(Set<Annotation> annos, TripleExpr exp){
		if(annos==null) {
			annos = new HashSet<Annotation>();
		}
		if(exp instanceof TripleConstraint) {
			TripleConstraint tc = (TripleConstraint)exp;
			annos.addAll(tc.getAnnotations());
		}else if (exp instanceof RepeatedTripleExpression) {
			RepeatedTripleExpression rtc = (RepeatedTripleExpression)exp;
			TripleExpr sub_exp = rtc.getSubExpression();
			annos = getAnnos(annos, sub_exp);
		}else if (exp instanceof EachOf) {
			EachOf rtc = (EachOf)exp;
			List<TripleExpr> sub_exps = rtc.getSubExpressions();
			for(TripleExpr sub_exp : sub_exps) {
				annos = getAnnos(annos, sub_exp);
			}
		} 
		return annos;
	}


	

}
