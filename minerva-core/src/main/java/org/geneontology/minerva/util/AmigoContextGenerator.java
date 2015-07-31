package org.geneontology.minerva.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.curie.DefaultCurieHandler;

import com.google.gson.Gson;

/**
 * generate: amigo_context_gen.jsonld from the json source:
 * http://build.berkeleybop.org/job/db-xrefs-yaml2json/lastSuccessfulBuild/artifact/db-xrefs.json
 */
public class AmigoContextGenerator {

	private final String sourceJson;
	private Map<String, String> existing; 
	
	public AmigoContextGenerator(String sourceJson, Map<String, String> existing) {
		this.sourceJson = sourceJson;
		this.existing = existing;
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, String> extract() throws Exception {
		Map<String, String> extracted = new HashMap<>();
		Gson gson = new Gson();
		List<?> jsonList = gson.fromJson(new InputStreamReader(new URL(sourceJson).openStream()), List.class);
		for (Object object : jsonList) {
			if (object instanceof Map) {
				Map<String, Object> topMap = (Map<String, Object>) object;
				String database =  (String) topMap.get("database");
				if (database != null) {
					// skip existing
					if (existing.containsKey(database)) {
						continue;
					}
					List<Map<String, Object>> entity_types = (List<Map<String, Object>>) topMap.get("entity_types");
					if (entity_types != null) {
						if (entity_types.size() == 1) {
							Map<String, Object> entity_type = entity_types.get(0);
							String url_syntax = (String) entity_type.get("url_syntax");
							if (url_syntax != null) {
								int pos = url_syntax.indexOf("[example_id]");
								if (pos > 0) {
									String longPrefix = url_syntax.substring(0, pos);
									if (existing.containsValue(longPrefix)) {
										System.out.println("Skipping: '"+database+"' conflicting longPrefix: "+longPrefix);
										continue;
									}
									if (extracted.containsValue(longPrefix)) {
										System.out.println("Skipping: '"+database+"' conflicting longPrefix: "+longPrefix);
										continue;
									}
									extracted.put(database, longPrefix);
								}
							}
							else {
								System.out.println("Missing url_syntax for: "+database);
							}
						}
						else {
							System.out.println("Manual mapping required for: "+database);
						}
						
					}
				}
			}
		}
		return extracted;
	}
	
	static void writeJsonLdContext(String file, Map<String, String> extracted) throws Exception {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(file));
			writer.append("{\n  \"@context\": {\n");
			List<String> sortedKeys = new ArrayList<>(extracted.keySet());
			Collections.sort(sortedKeys);
			for (int i = 0; i < sortedKeys.size(); i++) {
				if (i > 0) {
					writer.append(',');
					writer.newLine();
				}
				String key = sortedKeys.get(i);
				String value = extracted.get(key);
				writer.append("    ").append('"').append(key).append("\" : \"").append(value).append('"');
			}
			writer.newLine();
			writer.append("  }\n}");
		}
		finally {
			IOUtils.closeQuietly(writer);
		}
	}
	
	public static void main(String[] args) throws Exception {
		Map<String, String> existing = new HashMap<>();
		DefaultCurieHandler.loadJsonldResource("obo_context.jsonld", existing);
		DefaultCurieHandler.loadJsonldResource("monarch_context.jsonld", existing);
		String url = "http://build.berkeleybop.org/job/db-xrefs-yaml2json/lastSuccessfulBuild/artifact/db-xrefs.json";
		AmigoContextGenerator gen  = new AmigoContextGenerator(url, existing);
		Map<String, String> extracted = gen.extract();
		writeJsonLdContext("src/main/resources/amigo_context_gen.jsonld", extracted);
		

	}

}
