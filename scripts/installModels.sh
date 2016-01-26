mkdir -p models/
cd models/
git clone https://github.com/geneontology/noctua-models.git
git clone https://github.com/geneontology/go-site.git
svn --ignore-externals co svn://ext.geneontology.org/trunk/ontology
