PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX pdp: <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX dc: <http://purl.org/dc/terms/>

INSERT {
    GRAPH ?vocabularySnapshot {
    ?vocabularySnapshot a pdp:verze-slovníku ;
              pdp:je-verzí-slovníku ?vocabulary ;
              pdp:má-datum-a-čas-vytvoření-verze ?created ;
              dc:creator ?author ;
              pdp:má-glosář ?glossarySnapshot ;
              pdp:importuje-slovník ?importedSnapshot ;
              ?y ?z .
    ?glossarySnapshot a pdp:glosář ;
                      a pdp:verze-glosáře ;
                      a skos:ConceptScheme ;
              skos:hasTopConcept ?topConceptSnapshot ;
              pdp:je-verzí-glosáře ?glossary ;
              pdp:má-datum-a-čas-vytvoření-verze ?created .
    }
} WHERE {
    GRAPH ?context {
    ?vocabulary a pdp:slovník ;
                pdp:má-glosář ?glossary ;
                ?y ?z .
        OPTIONAL {
            ?glossary skos:hasTopConcept ?topConcept .
        }
        OPTIONAL {
            ?vocabulary pdp:importuje-slovník ?imported .
        }
    }
    BIND (IRI(CONCAT(str(?vocabulary), ?suffix)) as ?vocabularySnapshot)
    BIND (IRI(CONCAT(str(?glossary), ?suffix)) as ?glossarySnapshot)
    BIND (IRI(CONCAT(str(?topConcept), ?suffix)) as ?topConceptSnapshot)
    BIND (IRI(CONCAT(str(?imported), ?suffix)) as ?importedSnapshot)
    FILTER (?y NOT IN (pdp:má-glosář, pdp:popisuje-dokument, pdp:importuje-slovník, owl:imports))
}
