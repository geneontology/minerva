/**
 * 
 */
package org.geneontology.minerva.validation;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import fr.inria.lille.shexjava.schema.parsing.ShExJSerializer;

/**
 * @author bgood
 *
 */
public class Util {

	/**
	 * 
	 */
	public Util() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String schema_file = "../shapes/go-cam-shapes.shex";
		String schema_destination = "../shapes/generated-go-cam-shapes.shexj";
		shexctoshexj(schema_file, schema_destination);
	}

	public static void shexctoshexj(String shexc_schema_file, String shexj_schema_file) throws Exception {
		ShexSchema schema = GenParser.parseSchema(new File(shexc_schema_file).toPath());
		ShExJSerializer.ToJson(schema, new File(shexj_schema_file).toPath());
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
