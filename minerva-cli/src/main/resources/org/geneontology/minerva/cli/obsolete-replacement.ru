PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX dc: <http://purl.org/dc/elements/1.1/>

DELETE {
  GRAPH ?model {
    ?obsolete a owl:Class .
    ?model dc:date ?model_date .
    ?x a ?obsolete .
    ?x dc:date ?old_date .
    ?axiom owl:annotatedTarget ?obsolete .
    ?axiom dc:date ?axiom_date .
  }
}
INSERT {
  GRAPH ?model {
    ?replacement a owl:Class .
    ?model dc:date ?new_date .
    ?model rdfs:comment ?comment .
    ?x a ?replacement .
    ?x dc:date ?new_date .
    ?x rdfs:comment ?comment .
    ?axiom owl:annotatedTarget ?replacement .
    ?axiom dc:date ?new_date .
    ?axiom rdfs:comment ?comment .
  }
}
WHERE {
  VALUES (?obsolete ?replacement) { %%%values%%% }
  GRAPH ?model {
    VALUES (?obsolete ?replacement) { %%%values%%% }
    ?x a owl:NamedIndividual .
    ?x a ?obsolete .
    FILTER(?obsolete != owl:NamedIndividual)
    OPTIONAL {
      # For completeness, but currently rdf:type axioms do not have axiom annotations in Noctua models
      ?axiom a owl:Axiom ;
             owl:annotatedSource ?x ;
             owl:annotatedProperty rdf:type ;
             owl:annotatedTarget ?obsolete .
      OPTIONAL {
        ?axiom dc:date ?axiom_date .
      }
    }
    OPTIONAL {
      ?x dc:date ?old_date .
    }
    OPTIONAL {
      ?model dc:date ?model_date .
    }
    BIND(NOW() AS ?now)
    BIND(YEAR(?now) AS ?year_int)
    BIND(MONTH(?now) AS ?month_int)
    BIND(DAY(?now) AS ?day_int)
    BIND(STR(?year_int) AS ?year)
    BIND(IF(?month_int < 10, CONCAT("0", STR(?month_int)), STR(?month_int)) AS ?month)
    BIND(IF(?day_int < 10, CONCAT("0", STR(?day_int)), STR(?day_int)) AS ?day)
    BIND(STRDT(CONCAT(?year, "-", ?month, "-", ?day), xsd:string) AS ?new_date)
    BIND(STR(?obsolete) AS ?obsolete_str)
    BIND(STR(?replacement) AS ?replacement_str)
    BIND(CONCAT("Automated change ", ?new_date, ": <", ?obsolete_str, "> replaced_by <", ?replacement_str, ">") AS ?comment)
  }
}
