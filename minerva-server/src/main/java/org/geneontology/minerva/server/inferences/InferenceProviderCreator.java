package org.geneontology.minerva.server.inferences;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.json.InferenceProvider;

public interface InferenceProviderCreator {

    public InferenceProvider create(ModelContainer model) throws Exception;
}
