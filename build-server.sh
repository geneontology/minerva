#!/bin/bash
mvn clean package -am -pl minerva-server -DskipTests -Dmaven.javadoc.skip=true -Dsource.skip=true

