package org.geneontology.minerva.curie;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import com.google.common.collect.ImmutableBiMap;

public class MappedCurieHandler implements CurieHandler {

	private final ImmutableBiMap<String, String> curieMap;

	public MappedCurieHandler(CurieMappings...mappings) {
		this(merge(mappings));
	}
	
	private static Map<String, String> merge(CurieMappings[] mappings) {
		if (mappings.length == 0) {
			return Collections.emptyMap();
		}
		else if (mappings.length == 1) {
			return mappings[0].getMappings();
		}
		else {
			Map<String, String> curieMap = new HashMap<String, String>();
			for (CurieMappings mapping : mappings) {
				curieMap.putAll(mapping.getMappings());
			}
			return curieMap;
		}
	}
	
	public MappedCurieHandler(Map<String, String> curieMap) {
		super();
		this.curieMap = ImmutableBiMap.copyOf(curieMap);
	}

	@Override
	public String getCuri(OWLClass cls) {
		return getCuri(cls.getIRI());
	}

	@Override
	public String getCuri(OWLNamedIndividual i) {
		return getCuri(i.getIRI());
	}

	@Override
	public String getCuri(OWLObjectProperty p) {
		return getCuri(p.getIRI());
	}

	@Override
	public String getCuri(OWLDataProperty p) {
		return getCuri(p.getIRI());
	}

	@Override
	public String getCuri(OWLAnnotationProperty p) {
		return getCuri(p.getIRI());
	}

	@Override
	public String getCuri(IRI iri) {
		String iriString = iri.toString();
		String curi = iriString;
		
		String longPrefix = null;
		String shortPrefix = null;
		// iterate over inverted map, find longest prefix match
		for (Entry<String, String> e : curieMap.inverse().entrySet()) {
			String currentLongPrefix = e.getKey();
			int currentLongprefixLength = currentLongPrefix.length();
			if (iriString.startsWith(currentLongPrefix) && 
					iriString.length() > currentLongprefixLength) {
				if (longPrefix == null || currentLongprefixLength > longPrefix.length()) {
					longPrefix = currentLongPrefix;
					shortPrefix = e.getValue();
				}
			}
		}
		if (longPrefix != null) {
			return shortPrefix + ":" + iriString.substring(longPrefix.length());
		}
		return curi;
	}

	@Override
	public IRI getIRI(String curi) throws UnknownIdentifierException {
		if (!curi.contains(":")) {
			throw new UnknownIdentifierException("Relative IRIs are not allowed: " + curi);
		}
		String[] parts = StringUtils.split(curi, ":", 2);
		if (parts.length == 2) {
			String prefix = parts[0];
			String longPrefix = curieMap.get(prefix);
			if (longPrefix != null) {
				return IRI.create(longPrefix + curi.substring(prefix.length() + 1));
			}
		}
		if (curi.startsWith("http:") || curi.startsWith("https:") || curi.startsWith("urn:") || curi.startsWith("mailto:")) {
			return IRI.create(curi);			
		} else {
			throw new UnknownIdentifierException("Unknown URI protocol: " + curi);
		}
	}

	@Override
	public Map<String, String> getMappings() {
		return curieMap;
	}

	/**
	 * package private for internal test purposes.
	 * 
	 * @return map
	 */
	Map<String, String> getInternalMappings() {
		return curieMap;
	}
}
