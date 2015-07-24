package org.geneontology.minerva.curie;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class CurieMappingsJsonld implements CurieMappings {

	private static final Logger LOG = Logger.getLogger(CurieMappingsJsonld.class);
	
	private final Map<String, String> mappings;
	
	private static final CurieMappings EMPTY = new CurieMappingsJsonld(Collections.<String, String>emptyMap());
	
	private CurieMappingsJsonld(Map<String, String> mappings) {
		super();
		this.mappings = Collections.unmodifiableMap(mappings);
	}

	@Override
	public Map<String, String> getMappings() {
		return mappings;
	}

	public static CurieMappings loadJsonLdContext(InputStream inputStream) {
		try {
			String jsonld = IOUtils.toString(inputStream);
			return loadJsonLdContext(jsonld);
		} catch (IOException e) {
			LOG.error("Could not load JsonLD from input stream.", e);
		}
		return EMPTY;
	}
	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static CurieMappings loadJsonLdContext(String jsonldContent) {
		try {
			Gson gson = new Gson();
			Map topLevelMap = gson.fromJson(jsonldContent, Map.class);
			Map<String, String> parseMappings = new HashMap<String, String>();
			if (topLevelMap.containsKey("@context")) {
				Object jsonContext = topLevelMap.get("@context");
				if (jsonContext instanceof Map) {
					parseEntries((Map)jsonContext, parseMappings);
				}
			}
			parseEntries(topLevelMap, parseMappings);
			return new CurieMappingsJsonld(parseMappings);
		} catch (JsonSyntaxException e) {
			LOG.error("Could not parse JsonLD due to a JSON syntax problem.", e);
		}
		return EMPTY;
	}
	
	private static void parseEntries(Map<Object, Object> json, Map<String, String> parsedMappings) {
		for(Entry<Object, Object> e : json.entrySet()){
			Object key = e.getKey();
			Object value = e.getValue();
			if (key != null && value != null) {
				String shortPrefix = key.toString();
				if (shortPrefix.isEmpty() || shortPrefix.startsWith("@")) {
					continue;
				}
				if (value instanceof CharSequence) {
					String longPrefix = value.toString();
					if (longPrefix.isEmpty()) {
						continue;
					}
					parsedMappings.put(shortPrefix, longPrefix);
				}
			}
		}
	}
}
