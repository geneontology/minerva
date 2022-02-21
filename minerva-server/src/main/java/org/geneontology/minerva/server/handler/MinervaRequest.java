package org.geneontology.minerva.server.handler;


/**
 * Expected standard fields for a minerva request.
 *
 * @param <OPERATION>
 * @param <ENTITY>
 * @param <ARGUMENT>
 */
public abstract class MinervaRequest<OPERATION, ENTITY, ARGUMENT extends MinervaRequest.MinervaArgument> {

    ENTITY entity;
    OPERATION operation;
    ARGUMENT arguments;

    public abstract static class MinervaArgument {
        // empty for now
        // content depends on application
    }
}
