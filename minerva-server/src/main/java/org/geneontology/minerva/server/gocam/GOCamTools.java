package org.geneontology.minerva.server.gocam;

import java.util.Arrays;
import java.util.Objects;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.server.gocam.data.RootType;
import org.geneontology.minerva.server.gocam.entities.ActivityAssociation;
import org.geneontology.minerva.server.gocam.entities.Contributor;
import org.geneontology.minerva.server.gocam.entities.Evidence;
import org.geneontology.minerva.server.gocam.entities.TermAssociation;
import org.geneontology.minerva.server.gocam.entities.Term;
import org.geneontology.minerva.util.AnnotationShorthand;

public class GOCamTools {
	public static boolean isType(JsonOwlObject[] rootTypes, RootType rootType) {
		return Arrays.stream(rootTypes).anyMatch(x -> Objects.equals(x.id, rootType.id));
	}

	public static JsonOwlIndividual getNode(JsonOwlIndividual[] individuals, String uuid) {
		return Arrays.stream(individuals).filter(x -> Objects.equals(x.id, uuid)).findAny().orElse(null);
	}

	public static JsonOwlFact[] getFactsBySubject(JsonOwlFact[] facts, String subject) {
		return Arrays.stream(facts).filter(x -> Objects.equals(x.subject, subject)).toArray(JsonOwlFact[]::new);
	}

	public static Term getTermFromId(JsonOwlIndividual[] individuals, String uuid) {
		JsonOwlIndividual individual = GOCamTools.getNode(individuals, uuid);

		if (individual != null) {
			JsonOwlObject[] types = individual.type;
			if (types.length > 0) {
				JsonOwlObject typeObj = types[0];
				Term term = new Term(individual.id, typeObj, individual.annotations);
				return term;
			}
		}
		return null;
	}

	public static Evidence getEvidenceFromId(JsonOwlIndividual[] individuals, String uuid) {
		JsonOwlIndividual individual = GOCamTools.getNode(individuals, uuid);

		if (individual != null) {
			JsonOwlObject[] types = individual.type;
			if (types.length > 0) {
				JsonOwlObject typeObj = types[0];
				Evidence evidence = new Evidence(individual.id, typeObj, individual.annotations);
				return evidence;
			}
		}
		return null;
	}

	public static TermAssociation getTermAssociationFromFact(JsonOwlIndividual[] individuals, JsonOwlFact fact) {
		TermAssociation termAssociation = new TermAssociation(fact);
		termAssociation.addAnnotations(individuals, fact.annotations);

		return termAssociation;
	}
	
	public static ActivityAssociation getActivityAssociationFromFact(JsonOwlIndividual[] individuals, JsonOwlFact fact) {
		ActivityAssociation activityAssociation = new ActivityAssociation(fact);
		activityAssociation.addAnnotations(individuals, fact.annotations);

		return activityAssociation;
	}
}
