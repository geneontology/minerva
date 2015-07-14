package org.geneontology.minerva.util;

import static org.junit.Assert.*;

import org.geneontology.minerva.util.IdStringManager.AnnotationShorthand;
import org.junit.Test;

import com.google.gson.Gson;

public class AnnotationShortHandTest {

	@Test
	public void testRoundTrip() throws Exception {
		Gson gson = new Gson();
		for (AnnotationShorthand sh : AnnotationShorthand.values()) {
			String json = gson.toJson(sh).replaceAll("\"", ""); // to json, remove surrounding quotes
			AnnotationShorthand roundTrip = AnnotationShorthand.getShorthand(json);
			assertEquals(sh, roundTrip);
		}
	}
}
