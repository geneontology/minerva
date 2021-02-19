package org.geneontology.minerva.server.gocam;

import java.util.Arrays;
import java.util.Objects;

import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.server.gocam.data.RootType;

public class GOCamTools {
	public static boolean isType(JsonOwlObject[] rootTypes, RootType rootType) {
		return Arrays.stream(rootTypes).anyMatch(x -> Objects.equals(x.id, rootType.id));
	}

	public static JsonOwlIndividual getNode(JsonOwlIndividual[] individuals, String uuid) {
		return Arrays.stream(individuals)
				.filter(x -> Objects.equals(x.id, uuid))
				.findAny()
                .orElse(null);
	}
	
	public static JsonOwlFact[] getFactsBySubject(JsonOwlFact[] facts, String subject) {
		return Arrays.stream(facts)
				.filter(x -> Objects.equals(x.subject, subject))
				.toArray(JsonOwlFact[]::new);				
	}
}
