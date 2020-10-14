#!/bin/bash
mvn -v 
mvn clean package -am -pl minerva-cli -DskipTests -Dmaven.javadoc.skip=true -Dsource.skip=true -B
