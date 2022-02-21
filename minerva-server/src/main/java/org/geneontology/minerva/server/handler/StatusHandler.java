package org.geneontology.minerva.server.handler;


import org.geneontology.minerva.server.StartUpTool.MinervaStartUpConfig;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;


/**
 * Respond to queries for system status
 */
@Path("/status")
public class StatusHandler {

    private final MinervaStartUpConfig conf;
    private final Map<IRI, Set<OWLAnnotation>> ont_annosa;
    private final String started_at;

    public class Status {
        public String startup_date = started_at;
        // data configuration
        public String ontology;
        public String catalog;
        public String journalFile;
        public String exportFolder;
        public String modelIdPrefix;
        public String modelIdcurie;
        public String defaultModelState;
        public String golrUrl;
        public String monarchUrl;
        public String golrSeedUrl;
        public int golrCacheSize;
        public long golrCacheDuration;
        public String reasonerOpt;
        public String importantRelationParent = "not set";
        public Set<OWLObjectProperty> importantRelations;
        public int port;
        public String contextPrefix;
        public String contextString;
        public int requestHeaderSize;
        public int requestBufferSize;
        public boolean useRequestLogging;
        public boolean useGolrUrlLogging;
        public String prefixesFile;
        public int sparqlEndpointTimeout;
        public String shexFileUrl;
        public String goshapemapFileUrl;
        public Map<IRI, Set<OWLAnnotation>> ont_annos = ont_annosa;

        public Status(MinervaStartUpConfig conf) {
            this.ontology = conf.ontology;
            this.catalog = conf.catalog;
            this.journalFile = conf.journalFile;
            this.exportFolder = conf.exportFolder;
            this.modelIdPrefix = conf.modelIdPrefix;
            this.modelIdcurie = conf.modelIdcurie;
            this.defaultModelState = conf.defaultModelState;
            this.golrUrl = conf.golrUrl;
            this.monarchUrl = conf.monarchUrl;
            this.golrSeedUrl = conf.golrSeedUrl;
            this.golrCacheSize = conf.golrCacheSize;
            this.golrCacheDuration = conf.golrCacheDuration;
            this.reasonerOpt = conf.reasonerOpt;
            this.importantRelationParent = conf.importantRelationParent;
            this.importantRelations = conf.importantRelations;
            this.port = conf.port;
            this.contextPrefix = conf.contextPrefix;
            this.contextString = conf.contextString;
            this.requestHeaderSize = conf.requestHeaderSize;
            this.requestBufferSize = conf.requestBufferSize;
            this.useRequestLogging = conf.useRequestLogging;
            this.useGolrUrlLogging = conf.useGolrUrlLogging;
            this.prefixesFile = conf.prefixesFile;
            this.sparqlEndpointTimeout = conf.sparqlEndpointTimeout;
            this.shexFileUrl = conf.shexFileUrl;
            this.goshapemapFileUrl = conf.goshapemapFileUrl;

        }
    }

    /**
     * @param ont_annos
     * @param started_at
     */
    public StatusHandler(MinervaStartUpConfig conf, Map<IRI, Set<OWLAnnotation>> ont_annos, String started_at) {
        this.ont_annosa = ont_annos;
        this.conf = conf;
        this.started_at = started_at;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Status get() {
        return new Status(conf);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public MinervaStartUpConfig post() {
        return conf;
    }
}