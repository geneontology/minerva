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
	- [Minerva Configuration](#minerva-configuration)
		- [If one of the maven repos is offline, use a cached version.](#if-one-of-the-maven-repos-is-offline-use-a-cached-version)
		- [Avoid downloading OWL files, use the cache.](#avoid-downloading-owl-files-use-the-cache)
	- [Obtaining the supporting model and data files](#obtaining-the-supporting-model-and-data-files)
		- [Getting the Noctua, GO and LEGO data](#getting-the-noctua-go-and-lego-data)
		- [Updating the `ontology/extensions/catalog-v001.xml` catalog](#updating-the-ontologyextensionscatalog-v001xml-catalog)
	- [Obtaining `owl-models` and `go-lego.owl`](#obtaining-owl-models-and-go-legoowl)
		- [Useful source files for learning](#useful-source-files-for-learning)

<!-- /MarkdownTOC -->

# About this document

This is a brief overview on how to setup and test the Minerva server. This includes the building of the server, obtaining the required model and data files, configuration, and testing.

## Building the server

### Prerequisites to build the code

 * Java as compiler. Check with `java -version`. JDK 1.7 or higher is required. JDK 1.8 is recommended.
 * Maven Build-Tool. Check with `mvn --version`. 3.0.x required; 3.3.x or higher is recommended.


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

This should result in output that concludes with something similar to:

```
{"id":"ECO:0001177","label":"RNA dot blot assay evidence used in manual assertion"},{"id":"ECO:0000087","label":"immunolocalization evidence"},{"id":"ECO:0001056","label":"induced mutation evidence"},{"id":"ECO:0005531","label":"motif discovery evidence"},{"id":"ECO:0001176","label":"DNA laddering assay evidence used in manual assertion"},{"id":"ECO:0000088","label":"biological system reconstruction evidence"},{"id":"ECO:0001055","label":"immunohistochemistry evidence"},{"id":"ECO:0005530","label":"random mutagenesis evidence used in manual assertion"}]}}}
```

## Minerva Configuration

There are some non-default environment variables that are useful in some situations, especially in poor network conditions.

### If one of the maven repos is offline, use a cached version.

```
export MINERVA_MAVEN_OPTS="--offline --no-snapshot-updates"
```

### Avoid downloading OWL files, use the cache.

```
export GENEONTOLOGY_CATALOG=`pwd`/../models/ontology/extensions/catalog-v001.xml
```

## Obtaining the supporting model and data files

Some developers of Minerva may already have downloaded the various models and data files that describe the GO, LEGO and related structures. The instructions below are an attempt to provide a systematic way for developers to obtain the data. These instructions gather all of the model data under a `models/` root directory that is a sibling of the `minerva/` directory. When running Noctua or Minerva, there will be configuration (`startup.yaml`) or environment variables (e.g., `GENEONTOLOGY_CATALOG`) that point to files and directories within this `models/` tree

### Getting the Noctua, GO and LEGO data

These instructions assume you are located in your `minerva/` directory that contains this document.

```
cd ../ 				# Move up the directory containing minerva
mkdir models/
cd models/
git clone https://github.com/geneontology/noctua-models.git
git clone https://github.com/geneontology/go-site.git
svn --ignore-externals co svn://ext.geneontology.org/trunk/ontology
```

### Updating the `ontology/extensions/catalog-v001.xml` catalog

The file `models/ontology/extensions/catalog-v001.xml` has been constructed so that the component OWL models are loaded via a local cache, which dramatically improves performance. For example, the catalog has an Import Resolution entry which uses the cached copy `models/ontology/extensions/gorel.owl` when the resource `http://purl.obolibrary.org/obo/go/extensions/gorel.owl` is requested:

```
<uri
	id="User Entered Import Resolution"
	name="http://purl.obolibrary.org/obo/go/extensions/gorel.owl"
	uri="gorel.owl"/>
```

Unfortunately, the default repository at `svn://ext.geneontology.org/trunk/ontology` does not contain cached versions of several important and large OWL models:

- [wbphenotype.owl](http://purl.obolibrary.org/obo/wbphenotype.owl)
- [taxslim.owl](http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl)
- [taxslim-disjoint-over-in-taxon.owl](http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim-disjoint-over-in-taxon.owl)
- [eco.owl](http://purl.obolibrary.org/obo/eco.owl)
- [wbbt.owl](http://purl.obolibrary.org/obo/wbbt.owl)
- [neo.owl](http://purl.obolibrary.org/obo/go/noctua/neo.owl)

It is worth the trouble of updating the `models/` directory and the `catalog-v001.xml` catalog to avoid unnecessary downloads, especially if you are in a network-challenged environment. If you have `owltools` installed, you can adapt the following script to update your `models/` and catalog files:

```
#
# This should be turned into a useful script and placed in Git
# But for now, it may be helpful as-is
#
export CACHEDIR=./cache-supplemental
export CATALOG=./catalog-v001-supplemental.xml
export OWLTOOLS=~/MI/owltools/OWLTools-Runner/bin/owltools

echo "" > $CATALOG

for F in \
	http://purl.obolibrary.org/obo/wbphenotype.owl \
	http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl \
	http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim-disjoint-over-in-taxon.owl \
	http://purl.obolibrary.org/obo/eco.owl \
	http://purl.obolibrary.org/obo/wbbt.owl \
	http://purl.obolibrary.org/obo/go/noctua/neo.owl
do
	TMPCATALOG="./catalog-tmp.xml"

	echo "# Processing $F"
	$OWLTOOLS \
		$F \
		--slurp-import-closure \
		-d $CACHEDIR \
		-c $TMPCATALOG \
		--merge-imports-closure \
		-o $CACHEDIR/merged.owl
	echo "# Adding $CACHEDIR/$TMPCATALOG"
	cat $CACHEDIR/$TMPCATALOG >> $CATALOG
done
```

The result of this should be a subdirectory of `./cache-supplemental` called `purl.obolibrary.org` which contains a tree of OWL files, and a catalog file called `./catalog-v001-supplemental.xml`. The `purl.obolibrary.org` directory should be placed in the same directory as the *official* catalog file (e.g., `models/ontology/extensions/catalog-v001.xml`). The *official* catalog file should be extended by extracting the URI information from the above generated file and pasting it into near the end.

For example, after executing the above script, the following URI information should be pasted into the official catalog:


```
  <uri name="http://purl.obolibrary.org/obo/wbphenotype.owl" uri="purl.obolibrary.org/obo/wbphenotype.owl"/>
  <uri name="http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl" uri="purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl"/>
  <uri name="http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim-disjoint-over-in-taxon.owl" uri="purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim-disjoint-over-in-taxon.owl"/>
  <uri name="http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl" uri="purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl"/>
  <uri name="http://purl.obolibrary.org/obo/eco.owl" uri="purl.obolibrary.org/obo/eco.owl"/>
  <uri name="http://purl.obolibrary.org/obo/wbbt.owl" uri="purl.obolibrary.org/obo/wbbt.owl"/>
  <uri name="http://purl.obolibrary.org/obo/go/noctua/neo.owl" uri="purl.obolibrary.org/obo/go/noctua/neo.owl"/>
```


## Obtaining `owl-models` and `go-lego.owl`

See [Monarch Ontology](https://github.com/monarch-initiative/monarch-ontology) and use the instructions there to generate a `catalog-v001.xml`.

- ftp://ftp.geneontology.org/pub/go//experimental/lego/server/owl-models
- ftp://ftp.geneontology.org/pub/go//ontology/extensions/go-lego.owl


### Useful source files for learning

- `/minerva-server/src/main/java/org/geneontology/minerva/server/handler/M3BatchHandler.java`



