package org.bbop.golr.java;

import org.apache.http.client.methods.HttpPost;

import java.io.IOException;
import java.net.URI;
import java.util.*;

public class RetrieveGolrOntologyClass extends AbstractRetrieveGolr {

    static int PAGINATION_CHUNK_SIZE = 1000;

    private final List<String> relevantFields;

    public RetrieveGolrOntologyClass(String server, int retryCount) {
        super(server, retryCount);
        relevantFields = GolrOntologyClassDocument.getRelevantFields();
    }

    @Override
    protected boolean isIndentJson() {
        return true;
    }

    @Override
    protected List<String> getRelevantFields() {
        return relevantFields;
    }


    public Map<String, List<GolrOntologyClassDocument>> getGolrOntologyCls(Set<String> curies) throws IOException {
        List<String[]> tagvalues = new ArrayList<String[]>();
        String[] tagvalue = new String[1 + curies.size()];
        int i = 0;
        tagvalue[i] = "annotation_class";
        for (String curie : curies) {
            i++;
            tagvalue[i] = curie;
        }
        tagvalues.add(tagvalue);
        final List<GolrOntologyClassDocument> documents = getGolrOntologyCls(tagvalues);
        //remap
        Map<String, List<GolrOntologyClassDocument>> curie_response = new HashMap<String, List<GolrOntologyClassDocument>>();
        for (GolrOntologyClassDocument ontdoc : documents) {
            String id = ontdoc.annotation_class;
            List<GolrOntologyClassDocument> docs = curie_response.get(id);
            if (docs == null) {
                docs = new ArrayList<GolrOntologyClassDocument>();
            }
            docs.add(ontdoc);
            curie_response.put(id, docs);
        }

        return curie_response;
    }

    public List<GolrOntologyClassDocument> getGolrOntologyCls(String id) throws IOException {
        List<String[]> tagvalues = new ArrayList<String[]>();
        String[] tagvalue = new String[2];
        tagvalue[0] = "annotation_class";
        tagvalue[1] = id;
        tagvalues.add(tagvalue);
        final List<GolrOntologyClassDocument> documents = getGolrOntologyCls(tagvalues);
        return documents;
    }

    public List<GolrOntologyClassDocument> getGolrOntologyCls(List<String[]> tagvalues) throws IOException {
//		final URI uri = createGolrRequest(tagvalues, "ontology_class", 0, PAGINATION_CHUNK_SIZE);
//		final String jsonString = getJsonStringFromUri(uri);

        final HttpPost post = createGolrPostRequest(tagvalues, "ontology_class", 0, PAGINATION_CHUNK_SIZE);
        final String jsonString = getJsonStringFromPost(post);

        final GolrResponse<GolrOntologyClassDocument> response = parseGolrResponse(jsonString);
        final List<GolrOntologyClassDocument> documents = new ArrayList<GolrOntologyClassDocument>(response.numFound);
        documents.addAll(Arrays.<GolrOntologyClassDocument>asList(response.docs));
        if (response.numFound > PAGINATION_CHUNK_SIZE) {
            // fetch remaining documents
            int start = PAGINATION_CHUNK_SIZE;
            int end = response.numFound / PAGINATION_CHUNK_SIZE;
            if (response.numFound % PAGINATION_CHUNK_SIZE != 0) {
                end += 1;
            }
            end = end * PAGINATION_CHUNK_SIZE;
            while (start <= end) {
                URI uriPagination = createGolrRequest(tagvalues, "ontology_class", start, PAGINATION_CHUNK_SIZE);
                String jsonStringPagination = getJsonStringFromUri(uriPagination);
                GolrResponse<GolrOntologyClassDocument> responsePagination = parseGolrResponse(jsonStringPagination);
                documents.addAll(Arrays.asList(responsePagination.docs));
                start += PAGINATION_CHUNK_SIZE;
            }
        }
        return documents;
    }

    private static class GolrOntologyClassResponse extends GolrEnvelope<GolrOntologyClassDocument> {
        // empty
    }

    public static class GolrOntologyClassDocument {

        public String document_category;
        public String annotation_class;
        public String annotation_class_label;
        public String description;
        public String source;
        public String is_obsolete;
        public List<String> alternate_id;
        public List<String> replaced_by;
        public List<String> consider;
        public List<String> synonym;
        public List<String> subset;
        public List<String> definition_xref;
        public List<String> database_xref;
        //public List<String> isa_partof_closure;
        public List<String> isa_closure;
        public List<String> regulates_closure;
        public String only_in_taxon;
        public List<String> only_in_taxon_closure;


        static List<String> getRelevantFields() {
            // explicit list of fields, avoid "*" retrieval of unused fields
            return Arrays.asList("document_category",
                    "annotation_class",
                    "annotation_class_label",
                    "description",
                    "source",
                    "is_obsolete",
                    "alternate_id",
                    "replaced_by",
                    "synonym",
                    "subset",
                    "definition_xref",
                    "database_xref",
                    //"isa_partof_closure",
                    "isa_closure",
                    "regulates_closure",
                    "only_in_taxon",
                    "only_in_taxon_closure");
        }
    }

    private GolrResponse<GolrOntologyClassDocument> parseGolrResponse(String jsonString) throws IOException {
        return parseGolrResponse(jsonString, GolrOntologyClassResponse.class).response;
    }

}
