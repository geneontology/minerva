#!/bin/bash
# export MINERVA_MAVEN_OPTS="--offline --no-snapshot-updates"
mvn clean package ${MINERVA_MAVEN_OPTS} -am -pl minerva-server -DskipTests -Dmaven.javadoc.skip=true -Dsource.skip=true

