PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX dd: <http://onto.fel.cvut.cz/ontologies/data-description/>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX dc: <http://purl.org/dc/terms/>

INSERT {
    GRAPH ?vocabularySnapshot {
    ?vocabularySnapshot a dd:vocabulary-version, skos:ConceptScheme ;
              dd:is-version-of-vocabulary ?vocabulary ;
              dd:has-date-and-time-of-creation-of-version ?created ;
              dc:creator ?author ;
              dd:imports-vocabulary ?importedSnapshot ;
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
            ?vocabulary dd:imports-vocabulary ?imported .
        }
    }
    BIND (IRI(CONCAT(str(?vocabulary), ?suffix)) as ?vocabularySnapshot)
    BIND (IRI(CONCAT(str(?topConcept), ?suffix)) as ?topConceptSnapshot)
    BIND (IRI(CONCAT(str(?imported), ?suffix)) as ?importedSnapshot)
    FILTER (?y NOT IN (skos:hasTopConcept, dd:describes-document, dd:imports-vocabulary, owl:imports))
}
