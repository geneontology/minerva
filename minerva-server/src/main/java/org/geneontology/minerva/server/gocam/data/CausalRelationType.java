package org.geneontology.minerva.server.gocam.data;

import java.util.HashMap;
import java.util.Map;

public enum CausalRelationType {
	CAUSALLY_UPSTREAM_OF("RO:0002411", "causally upstream of"),
	CAUSALLY_UPSTREAM_OF_OR_WITHIN("RO:0002418", "causally upstream of or within"),
	CAUSALLY_UPSTREAM_OF_POSITIVE_EFFECT("RO:0002304", "causally upstream of, positive effect"),
	CAUSALLY_UPSTREAM_OF_NEGATIVE_EFFECT("RO:0002305", "causally upstream of, negative effect"),
	CAUSALLY_UPSTREAM_OF_OR_WITHIN_POSITIVE_EFFECT("RO:0004047", "causally upstream of or within, positive effect"),
	CAUSALLY_UPSTREAM_OF_OR_WITHIN_NEGATIVE_EFFECT("RO:0004046", "causally upstream of or within, negative effect"),
	DIRECTLY_PROVIDES_INPUT("RO:0002413", "directly provides input"), REGULATES("RO:0002211", "regulates"),
	POSITIVELY_REGULATES("RO:0002213", "positively regulates"),
	NEGATIVELY_REGULATES("RO:0002212", "negatively regulates"), DIRECTLY_REGULATES("RO:0002578", "directly regulates"),
	DIRECTLY_POSITIVELY_REGULATES("RO:0002629", "directly positively regulates"),
	DIRECTLY_NEGATIVELY_REGULATES("RO:0002630", "directly negatively regulates");

	private static final Map<String, CausalRelationType> BY_ID = new HashMap<>();
	private static final Map<String, CausalRelationType> BY_LABEL = new HashMap<>();

	static {
		for (CausalRelationType e : values()) {
			BY_ID.put(e.id, e);
			BY_LABEL.put(e.label, e);
		}
	}

	public final String id;
	public final String label;

	private CausalRelationType(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static CausalRelationType valueOfId(String id) {
		return BY_ID.get(id);
	}

	public static CausalRelationType valueOfLabel(String label) {
		return BY_LABEL.get(label);
	}

}
