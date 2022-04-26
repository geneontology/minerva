PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX dc: <http://purl.org/dc/elements/1.1/>

DELETE {
  GRAPH ?model {
    ?replaced a owl:ObjectProperty .
    ?model dc:date ?model_date .
    ?s ?replaced ?o .
    ?axiom owl:annotatedProperty ?replaced .
    ?axiom dc:date ?axiom_date .
  }
}
INSERT {
  GRAPH ?model {
    ?replacement a owl:ObjectProperty .
    ?model dc:date ?new_date .
    ?model rdfs:comment ?comment .
    ?s ?replacement ?o .
    ?axiom owl:annotatedProperty ?replacement .
    ?axiom dc:date ?new_date .
    ?axiom rdfs:comment ?comment .
  }
}
WHERE {
  VALUES (?replaced ?replaced_curie ?replacement ?replacement_curie) { %%%values%%% }
  GRAPH ?model {
    VALUES (?replaced ?replaced_curie ?replacement ?replacement_curie) { %%%values%%% }
    ?s ?replaced ?o .
    OPTIONAL {
      ?axiom a owl:Axiom ;
      owl:annotatedSource ?s ;
      owl:annotatedProperty ?replaced ;
      owl:annotatedTarget ?o .
      OPTIONAL {
        ?axiom dc:date ?axiom_date .
      }
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
    BIND(CONCAT("Automated change ", ?new_date, ": ", ?replaced_curie, " replaced by ", ?replacement_curie) AS ?comment)
  }
}
