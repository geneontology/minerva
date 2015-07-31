package org.geneontology.minerva.curie;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class DefaultCurieHandler {
	
	private static final Logger LOG = Logger.getLogger(DefaultCurieHandler.class);

	private DefaultCurieHandler() {
		// no instances
	}
	
	private static volatile CurieHandler DEFAULT_CURI_HANDLER = null;
	private static volatile CurieMappings DEFAULT_CURI_MAPPINGS = null;
	
	public static synchronized CurieHandler getDefaultHandler() {
		if (DEFAULT_CURI_HANDLER == null) {
			DEFAULT_CURI_HANDLER = new MappedCurieHandler(getMappings());
		}
		return DEFAULT_CURI_HANDLER;
	}
	
	public static synchronized CurieMappings getMappings() {
		if (DEFAULT_CURI_MAPPINGS == null) {
			DEFAULT_CURI_MAPPINGS = loadDefaultMappings();
		}
		return DEFAULT_CURI_MAPPINGS;
	}
	
	static CurieMappings loadDefaultMappings() {
		final Map<String, String> curieMap = new HashMap<String, String>();
		loadJsonldResource("obo_context.jsonld", curieMap);
		loadJsonldResource("monarch_context.jsonld", curieMap);
		loadJsonldResource("amigo_context_gen.jsonld", curieMap);
		loadJsonldResource("amigo_context_manual.jsonld", curieMap);
		return new CurieMappings.SimpleCurieMappings(curieMap);
	}
	
	public static void loadJsonldResource(String resource, Map<String, String> curieMap) {
		InputStream stream = loadResourceAsStream(resource);
		if (stream != null) {
			CurieMappings jsonldContext = CurieMappingsJsonld.loadJsonLdContext(stream);
			curieMap.putAll(jsonldContext.getMappings());
		}
		else {
			LOG.error("Could not find resource for default curie map: "+resource);
		}
	}

	//  package private for testing purposes
	static InputStream loadResourceAsStream(String resource) {
		InputStream stream = DefaultCurieHandler.class.getResourceAsStream(resource);
		if (stream != null) {
			return stream;
		}
		stream = ClassLoader.getSystemResourceAsStream(resource);
		if (stream != null) {
			return stream;
		}
		else if (resource.startsWith("/") == false) {
			return loadResourceAsStream("/"+resource);
		}
		return stream;
	}
	
}
