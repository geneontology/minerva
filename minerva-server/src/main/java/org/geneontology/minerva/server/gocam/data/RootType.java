package org.geneontology.minerva.server.gocam.data;

import java.util.HashMap;
import java.util.Map;

public enum RootType {
	GO_PROTEIN_CONTAINING_COMPLEX("GO:0032991", "protein-containing complex"),
	GO_CELLULAR_COMPONENT("GO:0005575", "cellular_component"), 
	GO_BIOLOGICAL_PROCESS("GO:0008150", "biological_process"),
	GO_MOLECULAR_FUNCTION("GO:0003674", "molecular_function"),
	GO_MOLECULAR_ENTITY("CHEBI:33695", "information biomacromolecule"),
	GO_CHEMICAL_ENTITY("CHEBI:24431", "chemical entity");

	private static final Map<String, RootType> BY_ID = new HashMap<>();
	private static final Map<String, RootType> BY_LABEL = new HashMap<>();

	static {
		for (RootType e : values()) {
			BY_ID.put(e.id, e);
			BY_LABEL.put(e.label, e);
		}
	}

	public final String id;
	public final String label;

	private RootType(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static RootType valueOfId(String id) {
		return BY_ID.get(id);
	}

	public static RootType valueOfLabel(String label) {
		return BY_LABEL.get(label);
	}

}