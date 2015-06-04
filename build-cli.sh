#!/bin/bash
mvn clean package -am -pl minerva-cli -DskipTests -Dmaven.javadoc.skip=true -Dsource.skip=true

