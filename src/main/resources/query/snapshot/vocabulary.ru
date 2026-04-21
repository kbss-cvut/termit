PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX pdp: <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX dc: <http://purl.org/dc/terms/>

INSERT {
    GRAPH ?vocabularySnapshot {
    ?vocabularySnapshot a pdp:verze-slovníku, skos:ConceptScheme ;
              pdp:je-verzí-slovníku ?vocabulary ;
              pdp:má-datum-a-čas-vytvoření-verze ?created ;
              dc:creator ?author ;
              pdp:importuje-slovník ?importedSnapshot ;
              skos:hasTopConcept ?topConceptSnapshot ;
              ?y ?z .
    }
} WHERE {
    GRAPH ?context {
        ?vocabulary a skos:ConceptScheme ;
            ?y ?z .
        OPTIONAL {
            ?vocabulary skos:hasTopConcept ?topConcept .
        }
        OPTIONAL {
            ?vocabulary pdp:importuje-slovník ?imported .
        }
    }
    BIND (IRI(CONCAT(str(?vocabulary), ?suffix)) as ?vocabularySnapshot)
    BIND (IRI(CONCAT(str(?topConcept), ?suffix)) as ?topConceptSnapshot)
    BIND (IRI(CONCAT(str(?imported), ?suffix)) as ?importedSnapshot)
    FILTER (?y NOT IN (skos:hasTopConcept, pdp:popisuje-dokument, pdp:importuje-slovník, owl:imports))
}
