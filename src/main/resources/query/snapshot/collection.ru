PREFIX dd: <http://onto.fel.cvut.cz/ontologies/data-description/>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

INSERT {
    GRAPH ?vocabularySnapshot {
        ?collectionSnapshot a dd:version-of-collection ;
               skos:inScheme ?vocabularySnapshot ;
               dd:is-version-of-collection ?collection ;
               dd:has-date-and-time-of-creation-of-version ?created ;
               skos:member ?memberSnapshot ;
               skos:memberList ?listHeadSnapshot ;
               ?y ?z .
        ?nodeSnapshot rdf:first ?nodeMemberSnapshot ;
               rdf:rest ?nextSnapshot .
    }
} WHERE {
    GRAPH ?context {
        # Collection in vocabulary
        ?collection a skos:Collection ;
            skos:inScheme ?vocabulary .

        {
            # additional properties (label, description)
            ?collection ?y ?z .
            FILTER (?y NOT IN (skos:member, skos:memberList, skos:inScheme))
        } UNION {
            # unordered collection members
            ?collection skos:member ?member .
        } UNION {
            # ordered collection list head
            ?collection skos:memberList ?listHead .
        } UNION {
            # ordered collection list nodes
            ?collection skos:memberList/rdf:rest* ?node .
            ?node rdf:first ?nodeMember .
            OPTIONAL {
                ?node rdf:rest ?next .
            }
        }
    }

    BIND (IRI(CONCAT(str(?vocabulary), ?suffix)) as ?vocabularySnapshot)
    BIND (IRI(CONCAT(str(?collection), ?suffix)) as ?collectionSnapshot)
    BIND (IRI(CONCAT(str(?member), ?suffix)) as ?memberSnapshot)
    BIND (IF(?listHead = rdf:nil, rdf:nil, IRI(CONCAT(str(?listHead), ?suffix))) as ?listHeadSnapshot)
    BIND (IRI(CONCAT(str(?node), ?suffix)) as ?nodeSnapshot)
    BIND (IRI(CONCAT(str(?nodeMember), ?suffix)) as ?nodeMemberSnapshot)
    BIND (IF(?next = rdf:nil, rdf:nil, IRI(CONCAT(str(?next), ?suffix))) as ?nextSnapshot)
}
