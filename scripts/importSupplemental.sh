# See: https://github.com/owlcollab/owltools/wiki/Import-Chain-Mirroring

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

cat  $CATALOG
exit

# $OWLTOOLS http://purl.obolibrary.org/obo/wbphenotype.owl --slurp-import-closure -d $CACHEDIR -c $CATALOG --merge-imports-closure -o $CACHEDIR/merged.owl
# $OWLTOOLS http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim.owl --slurp-import-closure -d $CACHEDIR -c $CATALOG --merge-imports-closure -o $CACHEDIR/merged.owl
# $OWLTOOLS http://purl.obolibrary.org/obo/ncbitaxon/subsets/taxslim-disjoint-over-in-taxon.owl --slurp-import-closure -d $CACHEDIR -c $CATALOG --merge-imports-closure -o $CACHEDIR/merged.owl
# $OWLTOOLS http://purl.obolibrary.org/obo/eco.owl --slurp-import-closure -d $CACHEDIR -c $CATALOG --merge-imports-closure -o $CACHEDIR/merged.owl
# $OWLTOOLS http://purl.obolibrary.org/obo/wbbt.owl --slurp-import-closure -d $CACHEDIR -c $CATALOG --merge-imports-closure -o $CACHEDIR/merged.owl
# $OWLTOOLS http://purl.obolibrary.org/obo/go/noctua/neo.owl --slurp-import-closure -d $CACHEDIR -c $CATALOG --merge-imports-closure -o $CACHEDIR/merged.owl
