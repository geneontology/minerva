package org.geneontology.minerva.util;

import static org.junit.Assert.assertEquals;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.Test;

public class AnnotationShortHandTest {

	@Test
	public void testRoundTrip() throws Exception {
		CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
		for (AnnotationShorthand sh : AnnotationShorthand.values()) {
			String iriString = sh.getAnnotationProperty().toString();
			String curie = curieHandler.getCuri(sh.getAnnotationProperty());
			String json = sh.getShorthand();
			AnnotationShorthand roundTrip = AnnotationShorthand.getShorthand(json, curieHandler);
			AnnotationShorthand resolvedByIRI = AnnotationShorthand.getShorthand(iriString, curieHandler);
			AnnotationShorthand resolvedByCurie = AnnotationShorthand.getShorthand(curie, curieHandler);
			assertEquals(sh, roundTrip);
			assertEquals(sh, resolvedByIRI);
			assertEquals(sh, resolvedByCurie);
		}
	}
}
