package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.PropertiesSpecification;
import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;

import java.util.Map;

@StaticMetamodel(Term.class)
public abstract class Term_ extends AbstractTerm_ {

    public static volatile SetAttribute<Term, MultilingualString> altLabels;

    public static volatile SetAttribute<Term, MultilingualString> hiddenLabels;

    public static volatile SingularAttribute<Term, MultilingualString> description;

    public static volatile SetAttribute<Term, String> notations;

    public static volatile SetAttribute<Term, MultilingualString> examples;

    public static volatile SetAttribute<Term, String> sources;

    public static volatile SetAttribute<Term, TermInfo> exactMatchTerms;

    public static volatile SetAttribute<Term, Term> parentTerms;

    public static volatile SetAttribute<Term, Term> externalParentTerms;

    public static volatile SetAttribute<Term, TermInfo> related;

    public static volatile SetAttribute<Term, TermInfo> relatedMatch;

    public static volatile SingularAttribute<Term, TermDefinitionSource> definitionSource;

    public static volatile PropertiesSpecification<Term, Map, String, String> properties;
}
