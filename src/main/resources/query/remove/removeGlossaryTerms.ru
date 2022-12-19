PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX pdp: <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/>

DELETE WHERE {
    GRAPH ?g {
        ?t a skos:Concept ;
           ?y ?z .
    }
    ?t pdp:je-pojmem-ze-slovníku ?vocabulary .
}