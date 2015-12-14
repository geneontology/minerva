mvn package -am -pl minerva-server -Dmaven.javadoc.skip=true -Dsource.skip=true -DskipTests

echo "------------------"
echo "------------------"
echo "------------------"

export MINERVA_MEMORY=2G
export MINERVA_PORT=6800
export GO_ROOT=./cache
export NOCTUA_MODELS=../noctua-models/models
export MINERVA_SERVER=./minerva-server/bin/minerva-server.jar

java \
	-Xmx${MINERVA_MEMORY} \
	-jar ${MINERVA_SERVER} \
	-g ${GO_ROOT}/merged.owl \
	-f ${NOCTUA_MODELS} \
	--port ${MINERVA_PORT} \
	-c ${GO_ROOT}/catalog-v001.xml \
	--use-golr-url-logging \
	--slme-elk \
	--use-golr-url-logging \
	--use-golr-url-logging \
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
