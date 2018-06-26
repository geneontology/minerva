# See: https://github.com/geneontology/go-site/issues/617
# Note: we will later switch to doing this as part of the GO release
minerva-core/src/main/resources/go_context.jsonld:
	wget --no-check-certificate https://raw.githubusercontent.com/prefixcommons/biocontext/master/registry/go_context.jsonld -O $@
