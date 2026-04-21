PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

DELETE WHERE {
    GRAPH ?g {
        ?t a skos:Concept ;
           skos:inScheme ?vocabulary ;
           ?y ?z .
    }
}
