[![Build Status](https://travis-ci.org/geneontology/minerva.svg?branch=master)](https://travis-ci.org/geneontology/minerva)

A server for [Noctua](https://github.com/geneontology/noctua/)

## Usage

To build and launch a server, see [INSTRUCTIONS.md](INSTRUCTIONS.md)

## About

Minerva is a wrapper and server for the OWL API and a triplestore (currently blazegraph) that serves as the back end for
Noctua. It communicates with Noctua via Barista. It gains its knowledge of the world through a Golr instance.

For specifications, see [specs/](specs)

Request API: https://github.com/berkeleybop/bbop-manager-minerva/wiki/MinervaRequestAPI

## Code

* minerva-core : core logic
* minerva-json : conversion to and from the JSON-LD esque transport and model exchange format
* minerva-converter : converter to/from other formats. Primarily GAF/GPAD
* minerva-lookup : To be deprecated? Non-generic functions for looking up genes in golr
* minerva-server : JAX-RS server
* minerva-cli : command line interface
