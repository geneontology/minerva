package org.geneontology.minerva.legacy.sparql;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GPADRendererTest {

    @Test
    public void rendersGPAD20FieldsAndHeaders() {
        IRI relation = IRI.create("http://purl.obolibrary.org/obo/RO_0002264");
        IRI extensionRelation = IRI.create("http://purl.obolibrary.org/obo/BFO_0000066");
        Map<IRI, String> shorthandIndex = new HashMap<>();
        shorthandIndex.put(relation, "acts_upstream_of_or_within");
        shorthandIndex.put(extensionRelation, "occurs_in");
        Clock clock = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);
        GPADRenderer renderer = new GPADRenderer(DefaultCurieHandler.getDefaultHandler(), shorthandIndex, clock);

        Set<GPADData.ConjunctiveExpression> extensions = Collections.singleton(new GPADData.ConjunctiveExpression() {
            @Override
            public IRI getRelation() {
                return extensionRelation;
            }

            @Override
            public IRI getFiller() {
                return IRI.create("http://purl.obolibrary.org/obo/CL_0000236");
            }
        });
        Set<Pair<String, String>> properties = new HashSet<>(Arrays.asList(
                Pair.of("noctua-model-id", "gomodel:123"),
                Pair.of("contributor", "https://orcid.org/0000-0002-1706-4196")));
        DefaultGPADData data = new DefaultGPADData(
                IRI.create("http://identifiers.org/mgi/MGI:123"),
                relation,
                IRI.create("http://purl.obolibrary.org/obo/GO_0008150"),
                extensions,
                "PMID:12345",
                IRI.create("http://purl.obolibrary.org/obo/ECO_0000314"),
                Optional.of("MGI:1,MGI:2"),
                Optional.of(IRI.create("http://purl.obolibrary.org/obo/NCBITaxon_10090")),
                "20260729",
                "MGI",
                properties);
        data.setOperator(GPADOperatorStatus.NOT);

        String expectedRow = "MGI:MGI:123\tNOT\tRO:0002264\tGO:0008150\tPMID:12345\tECO:0000314\tMGI:1,MGI:2\tNCBITaxon:10090\t2026-07-29\tMGI\tBFO:0000066(CL:0000236)\tcontributor-id=orcid:0000-0002-1706-4196|noctua-model-id=gomodel:123";
        Assert.assertEquals(expectedRow, renderer.render(data));
        Assert.assertEquals(
                "!gpad-version: 2.0\n" +
                        "!generated-by: GO_Noctua\n" +
                        "!date-generated: 2026-07-30\n" +
                        expectedRow + "\n",
                renderer.renderAll(Collections.singleton(data)));

        data.setOperator(GPADOperatorStatus.NONE);
        Assert.assertEquals("", renderer.render(data).split("\\t", -1)[1]);
    }
}
