package cz.cvut.kbss.termit.model.resource;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

import java.net.URI;

@StaticMetamodel(Document.class)
public abstract class Document_ extends Resource_ {

    public static volatile SetAttribute<Document, File> files;

    public static volatile SingularAttribute<Document, URI> vocabulary;
}
