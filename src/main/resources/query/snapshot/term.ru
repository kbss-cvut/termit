PREFIX pdp: <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX sioc: <http://rdfs.org/sioc/ns#>

INSERT {
    GRAPH ?vocabularySnapshot {
        ?tSnapshot a skos:Concept, pdp:verze-pojmu ;
               skos:inScheme ?glossarySnapshot ;
               skos:broader ?broaderSnapshot ;
               skos:broadMatch ?broadMatchSnapshot ;
               skos:related ?relatedSnapshot ;
               skos:relatedMatch ?relatedMatchSnapshot ;
               skos:exactMatch ?exactMatchSnapshot ;
               pdp:je-verzí-pojmu ?t ;
               pdp:má-datum-a-čas-vytvoření-verze ?created ;
               sioc:has_creator ?author ;
               ?y ?z .
    }
} WHERE {
    ?t pdp:je-pojmem-ze-slovníku ?vocabulary .
    GRAPH ?context {
    ?t a skos:Concept ;
       skos:inScheme ?glossary ;
       ?y ?z .
       OPTIONAL {
           ?t skos:broader ?broader .
       }
        OPTIONAL {
           ?t skos:broadMatch ?broadMatch .
       }
       OPTIONAL {
       	   ?t skos:related ?related .
       }
       OPTIONAL {
           ?t skos:relatedMatch ?relatedMatch .
       }
       OPTIONAL {
           ?t skos:exactMatch ?exactMatch .
       }
    }
    FILTER (?y NOT IN (skos:related, skos:relatedMatch, skos:exactMatch, skos:broader, skos:broadMatch, skos:narrower, skos:narrowMatch, skos:topConceptOf, skos:inScheme))

    BIND (IRI(CONCAT(str(?vocabulary), ?suffix)) as ?vocabularySnapshot)
    BIND (IRI(CONCAT(str(?glossary), ?suffix)) as ?glossarySnapshot)
    BIND (IRI(CONCAT(str(?t), ?suffix)) as ?tSnapshot)
    BIND (IRI(CONCAT(str(?related), ?suffix)) as ?relatedSnapshot)
    BIND (IRI(CONCAT(str(?relatedMatch), ?suffix)) as ?relatedMatchSnapshot)
    BIND (IRI(CONCAT(str(?broader), ?suffix)) as ?broaderSnapshot)
    BIND (IRI(CONCAT(str(?broadMatch), ?suffix)) as ?broadMatchSnapshot)
    BIND (IRI(CONCAT(str(?exactMatch), ?suffix)) as ?exactMatchSnapshot)
}
