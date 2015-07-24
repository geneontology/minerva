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
	
	private static volatile CurieHandler CURI_HANDLER = null;
	
	public static synchronized CurieHandler getDefaultHandler() {
		if (CURI_HANDLER == null) {
			CURI_HANDLER = loadDefaultHandler();
		}
		return CURI_HANDLER;
	}
	
	static CurieHandler loadDefaultHandler() {
		Map<String, String> curieMap = new HashMap<String, String>();
		loadJsonldResource("obo_context.jsonld", curieMap);
		loadJsonldResource("monarch_context.jsonld", curieMap);
		return new MappedCurieHandler(curieMap);
	}
	
	private static void loadJsonldResource(String resource, Map<String, String> curieMap) {
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
