# Configurable Minerva options

This document covers some of the configurable aspects of Minerva.

## Model ID prefix

The model ID prefix is used when constructing IRIs to name new models and individuals (which are based on their
containing model ID). The default is `http://model.geneontology.org/`, however this can be changed via a command-line
argument for most CLI commands and the server startup. E.g. `--model-id-prefix 'http://model.myproject.org/'`.

*TODO: check consistency of argument names across CLI commands.*
