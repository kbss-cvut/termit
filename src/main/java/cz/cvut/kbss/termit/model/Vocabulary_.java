package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.*;
import cz.cvut.kbss.termit.model.resource.Document;

import java.net.URI;
import java.util.Map;

@StaticMetamodel(Vocabulary.class)
public abstract class Vocabulary_ extends Asset_ {

    public static volatile SingularAttribute<Vocabulary, String> label;

    public static volatile SingularAttribute<Vocabulary, String> description;

    public static volatile SingularAttribute<Vocabulary, Document> document;

    public static volatile SingularAttribute<Vocabulary, Glossary> glossary;

    public static volatile SingularAttribute<Vocabulary, Model> model;

    public static volatile SetAttribute<Vocabulary, URI> importedVocabularies;

    public static volatile SingularAttribute<Vocabulary, URI> acl;

    public static volatile PropertiesSpecification<Vocabulary, Map, String, String> properties;

    public static volatile TypesSpecification<Vocabulary, String> types;
}
