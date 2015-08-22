#!/bin/bash
set -e
# Any subsequent commands which fail will cause the shell script to exit immediately

## Change for different max memory settings 
MINERVA_MEMORY="4G"
## Default Minerva Port
MINERVA_PORT=6800

## Check that exactly one command-line argument is set
if [ $# -ne 3 ]
  then
    echo "Exactly three arguments required: GO_SVN NOCTUA_MODELS MINERVA_LABEL_RESOLUTION"
    exit 1
fi

## Use command-line input as the location of the GO_SVN
## Remove trailing slash
GO_SVN=${1%/}
NOCTUA_MODELS=$2
MINERVA_LABEL_RESOLUTION=$3

## start Minerva
# use catalog xml and PURLs
java "-Xmx$MINERVA_MEMORY" -jar minerva-server.jar \
--use-request-logging \
--slme-elk \
--skip-class-id-validation \
--golr-labels $MINERVA_LABEL_RESOLUTION \
-c "$GO_SVN"/ontology/extensions/catalog-v001.xml \
-g http://purl.obolibrary.org/obo/go/extensions/go-lego.owl \
--set-important-relation-parent http://purl.obolibrary.org/obo/LEGOREL_0000000 \
-f "$NOCTUA_MODELS" \
--port $MINERVA_PORT
