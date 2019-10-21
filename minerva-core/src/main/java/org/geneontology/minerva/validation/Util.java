/**
 * 
 */
package org.geneontology.minerva.validation;

import java.io.File;

import fr.inria.lille.shexjava.schema.ShexSchema;
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
}
