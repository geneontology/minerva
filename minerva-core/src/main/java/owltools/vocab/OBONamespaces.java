package owltools.vocab;

public enum OBONamespaces {
	GO("GO"),
	BFO("BFO"),
	GOREL("GOREL"),
	RO("RO");
	
	final String ns;
	OBONamespaces(String ns) {
		this.ns = ns;
	}
}
