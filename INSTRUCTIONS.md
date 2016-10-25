<!-- MarkdownTOC -->

- [About this document](#about-this-document)
	- [Building the server](#building-the-server)
		- [Prerequisites to build the code](#prerequisites-to-build-the-code)
		- [Building the Minerva Server](#building-the-minerva-server)
	- [Running the Minerva Server](#running-the-minerva-server)
		- [Prerequisites](#prerequisites)
		- [Start the MolecularModelManager server from the command line](#start-the-molecularmodelmanager-server-from-the-command-line)
		- [Start Server via Eclipse:](#start-server-via-eclipse)
	- [Running Tests](#running-tests)
		- [Failing Tests](#failing-tests)
		- [Quick Test via `curl`](#quick-test-via-curl)
	- [Obtaining `owl-models` and `go-lego.owl`](#obtaining-owl-models-and-go-legoowl)
		- [Useful source files for learning](#useful-source-files-for-learning)

<!-- /MarkdownTOC -->

# About this document

This is a quick overview on how to setup a Java server for the MolecularModelManager (Minerva).

## Building the server

### Prerequisites to build the code

 * Java (JDK 1.8 or later) as compiler
 * Maven (3.0.x) Build-Tool

### Building the Minerva Server

```
 ./build-server.sh
```

## Running the Minerva Server

### Prerequisites

* go-lego.owl (GO-SVN/trunk/ontology/extensionx/go-lego.owl) and catalog.xml to local copies
* folder with model files (GO-SVN/trunk/experimental/lego/server/owl-models/)

### Start the MolecularModelManager server from the command line

* Build the code, will result in a jar
* Check memory settings in start-m3-server.sh, change as needed.
* The start script is in the bin folder: start-m3-server.sh

The Minerva server expects parameters for:

```
  -g path-to/go-lego.owl
  -f path-to/owl-models
  [--port 6800]
  [-c path-to/catalog.xml]
```

For more details and options, please check the source code of owltools.gaf.lego.server.StartUpTool

Full example using a catalog.xml, IRIs and assumes a full GO-SVN trunk checkout:

```
start-m3-server.sh -c go-trunk/ontology/extensions/catalog-v001.xml \
-g http://purl.obolibrary.org/obo/go/extensions/go-lego \
-f go-trunk/experimental/lego/server/owl-models \
--port 6800
```

### Start Server via Eclipse:

* Requires all data (go and models)
* Build in eclipse, start as main with appropriate parameters.


## Running Tests

```
	mvn -Dtest=FindGoCodesTest.testFindShortEvidence,LegoToGeneAnnotationTranslatorTest.testZfinExample test
```

### Failing Tests

mvn -e -DfailIfNoTests=false -Dtest=FindGoCodesTest test

https://raw.githubusercontent.com/evidenceontology/evidenceontology/master/gaf-eco-mapping.txt

[Maven CLI](http://maven.apache.org/ref/3.3.9/maven-embedder/cli.html)


### Quick Test via `curl`

This assumes you are in the `minerva/` directory, which is the parent of `minerva-server/`.

```
curl localhost:6800/`cat minerva-server/src/test/resources/server-test/long-get.txt`
```

## Obtaining `owl-models` and `go-lego.owl`

See [Monarch Ontology](https://github.com/monarch-initiative/monarch-ontology) and use the instructions there to generate a `catalog-v001.xml`.

- ftp://ftp.geneontology.org/pub/go//experimental/lego/server/owl-models
- ftp://ftp.geneontology.org/pub/go//ontology/extensions/go-lego.owl

### Useful source files for learning

- `/minerva-server/src/main/java/org/geneontology/minerva/server/handler/M3BatchHandler.java`


## Using the Blazegraph model store

### Create a new Blazegraph journal from a directory of existing model files

`minerva-cli.sh --import-owl-models -j blazegraph.jnl -f models`

### Dump model files from a Blazegraph journal that is not in use

`minerva-cli.sh --dump-owl-models -j blazegraph.jnl -f models`

### Start the Minerva Server with configuration for Blazegraph journal and model dump folder

`java "-Xmx$MINERVA_MEMORY" -jar minerva-server.jar -c catalog-v001.xml -g http://purl.obolibrary.org/obo/go/extensions/go-lego.owl -f blazegraph.jnl --export-folder exported-models --port 9999 --use-request-logging --slme-elk --skip-class-id-validation --set-important-relation-parent http://purl.obolibrary.org/obo/LEGOREL_0000000`

Note the options `-f blazegraph.jnl` for specifying the journal file and `--export-folder exported-models` for specifying where to write OWL models in response to a `export-all` operation request.

### Request an OWL dump of all models from a running Minerva Server

`curl 'http://localhost:3400/api/minerva_local/m3Batch?token=&intention=query&requests=%5B%7B%22entity%22%3A%22meta%22%2C%22operation%22%3A%22export-all%22%2C%22arguments%22%3A%7B%7D%7D%5D'`

This will output to the folder configured in the startup arguments.
