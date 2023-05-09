package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;

import java.net.URI;

@StaticMetamodel(AbstractTerm.class)
public abstract class AbstractTerm_ extends Asset_ {

    public static volatile SingularAttribute<AbstractTerm, MultilingualString> label;

    public static volatile SingularAttribute<AbstractTerm, MultilingualString> definition;

    public static volatile SingularAttribute<AbstractTerm, URI> glossary;

    public static volatile SingularAttribute<AbstractTerm, URI> vocabulary;

    public static volatile SingularAttribute<AbstractTerm, Boolean> draft;

    public static volatile TypesSpecification<AbstractTerm, String> types;
}
