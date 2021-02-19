package org.geneontology.minerva.server.gocam.data;

import java.util.HashMap;
import java.util.Map;

public enum RelationType {
	ENABLED_BY("RO:0002333", "enabled by"), 
	OCCURS_IN("BFO:0000066", "occurs in"),
	PART_OF("BFO:0000050", "part of");

	private static final Map<String, RelationType> BY_ID = new HashMap<>();
	private static final Map<String, RelationType> BY_LABEL = new HashMap<>();

	static {
		for (RelationType e : values()) {
			BY_ID.put(e.id, e);
			BY_LABEL.put(e.label, e);
		}
	}

	public final String id;
	public final String label;

	private RelationType(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static RelationType valueOfId(String id) {
		return BY_ID.get(id);
	}

	public static RelationType valueOfLabel(String label) {
		return BY_LABEL.get(label);
	}

}
