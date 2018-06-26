package org.geneontology.minerva.curie;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class DefaultCurieHandler {
	
	private static final Logger LOG = Logger.getLogger(DefaultCurieHandler.class);

	private DefaultCurieHandler() {
		// no instances
	}
	
	public static synchronized CurieHandler getDefaultHandler() {
		return new MappedCurieHandler(loadDefaultMappings());
	}
	
	public static CurieHandler getHandlerForFile(File jsonld) throws FileNotFoundException {
		return new MappedCurieHandler(loadMappingsFromFile(jsonld));
	}
	
	public static CurieMappings loadMappingsFromFile(File jsonld) throws FileNotFoundException {
		final Map<String, String> curieMap = new HashMap<String, String>();
		loadJsonldStream(new FileInputStream(jsonld), curieMap);
		return new CurieMappings.SimpleCurieMappings(curieMap);
	}
	
	public static CurieMappings loadDefaultMappings() {
		final Map<String, String> curieMap = new HashMap<String, String>();
        // TODO: we believe we only need obo_context and go_context
        // See: https://github.com/geneontology/go-site/issues/617
		loadJsonldResource("obo_context.jsonld", curieMap);
        loadJsonldResource("go_context.jsonld", curieMap);
 		//loadJsonldResource("monarch_context.jsonld", curieMap);
		//loadJsonldResource("amigo_context_gen.jsonld", curieMap);
		//loadJsonldResource("amigo_context_manual.jsonld", curieMap);
		return new CurieMappings.SimpleCurieMappings(curieMap);
	}
	
	public static void loadJsonldStream(InputStream stream, Map<String, String> curieMap) {
		CurieMappings jsonldContext = CurieMappingsJsonld.loadJsonLdContext(stream);
		curieMap.putAll(jsonldContext.getMappings());
	}
	
	public static void loadJsonldResource(String resource, Map<String, String> curieMap) {
		InputStream stream = loadResourceAsStream(resource);
		if (stream != null) {
			loadJsonldStream(stream, curieMap);
		}
		else {
			LOG.error("Could not find resource for default curie map: " + stream);
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
