mvn package ${MINERVA_MAVEN_OPTS} -am -pl minerva-server -Dmaven.javadoc.skip=true -Dsource.skip=true -DskipTests

echo "------------------"
echo "------------------"
echo "------------------"

# java -Xmx4G -cp ./java/lib/minerva-cli.jar org.geneontology.minerva.server.StartUpTool --use-golr-url-logging --use-request-logging --slme-elk -g http://purl.obolibrary.org/obo/go/extensions/go-lego.owl --set-important-relation-parent http://purl.obolibrary.org/obo/LEGOREL_0000000 --golr-labels http://noctua-golr.berkeleybop.org/ --golr-seed http://golr.berkeleybop.org/ -c ~/MI/models/ontology/extensions/catalog-v001.xml -f ~MI/models/noctua-models/models/ --port 6800
export MINERVA_MEMORY=5G
export MINERVA_PORT=6800
export MINERVA_SERVER=./minerva-server/bin/minerva-server.jar
export MODELSLOC=../models
export NOCTUA_MODELS=$MODELSLOC/noctua-models/models
export GO_ROOT=$MODELSLOC/ontology/extensions/
export GFLAG=http://purl.obolibrary.org/obo/upheno/monarch.owl 	# ${GO_ROOT}/go-lego.owl
export CATALOG=../amigo/monarchOntologyCore/cached-models/catalog.xml
# export GOLR_NEO_LOOKUP_URL=http://noctua-golr.berkeleybop.org/
# export GOLR_LOOKUP_URL=http://golr.berkeleybop.org/
export GOLR_NEO_LOOKUP_URL=http://solr-dev.monarchinitiative.org/solr/ontology
export GOLR_LOOKUP_URL=http://solr-dev.monarchinitiative.org/solr/ontology

java \
	-Xmx${MINERVA_MEMORY} \
	-jar ${MINERVA_SERVER} \
	-g ${GFLAG} \
	-f ${NOCTUA_MODELS} \
	--port ${MINERVA_PORT} \
	-c ${CATALOG} \
	--slme-elk \
	--use-golr-url-logging \
	--set-important-relation-parent http://purl.obolibrary.org/obo/LEGOREL_0000000 \
	--golr-labels ${GOLR_NEO_LOOKUP_URL} \
	--golr-seed ${GOLR_LOOKUP_URL}

exit


# gulp.task('run-minerva', shell.task(_run_cmd(
#     ['java',
#      '-Xmx' + minerva_max_mem + 'G',
#      '-cp', './java/lib/minerva-cli.jar',
#      'org.geneontology.minerva.server.StartUpTool',
#      '--use-golr-url-logging',
#      '--use-request-logging',
#      '--slme-elk',
#      '-g', 'http://purl.obolibrary.org/obo/go/extensions/go-lego.owl',
#      '--set-important-relation-parent', 'http://purl.obolibrary.org/obo/LEGOREL_0000000',
#      '--golr-labels', golr_neo_lookup_url,
#      '--golr-seed', golr_lookup_url,
#      '-c', geneontology_catalog,
#      '-f', noctua_models,
#      '--port', minerva_port
#     ]
# )));


# java \
# 	-Xmx${MINERVA_MEMORY} \
# 	-jar ${MINERVA_SERVER} \
# 	-c ${GO_SVN}/ontology/extensions/catalog-v001.xml \
# 	-g http://purl.obolibrary.org/obo/go/extensions/go-lego.owl \
# 	--obsolete-import http://purl.obolibrary.org/obo/go.owl \
# 	--obsolete-import http://purl.obolibrary.org/obo/go/extensions/x-disjoint.owl \
# 	--obsolete-import http://purl.obolibrary.org/obo/ro.owl \
# 	--obsolete-import http://purl.obolibrary.org/obo/go/extensions/ro_pending.owl \
# 	--obsolete-import http://purl.obolibrary.org/obo/eco.owl \
# 	--set-important-relation-parent http://purl.obolibrary.org/obo/LEGOREL_0000000 \
# 	-f ${GO_SVN}/experimental/lego/server/owl-models \
# 	--gaf-folder ${GO_SVN}/gene-associations \
# 	-p ${GO_SVN}/experimental/lego/server/protein/subset \
# 	--port ${MINERVA_PORT}
