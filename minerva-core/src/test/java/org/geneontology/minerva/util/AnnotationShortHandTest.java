package org.geneontology.minerva.util;

import static org.junit.Assert.assertEquals;

import org.geneontology.minerva.util.IdStringManager.AnnotationShorthand;
import org.junit.Test;

public class AnnotationShortHandTest {

	@Test
	public void testRoundTrip() throws Exception {
		for (AnnotationShorthand sh : AnnotationShorthand.values()) {
			String json = sh.getShorthand();
			AnnotationShorthand roundTrip = AnnotationShorthand.getShorthand(json);
			assertEquals(sh, roundTrip);
		}
	}
}
