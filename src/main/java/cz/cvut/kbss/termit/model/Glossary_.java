package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

import java.net.URI;

@StaticMetamodel(Glossary.class)
public abstract class Glossary_ extends AbstractEntity_{

    public static volatile SetAttribute<Glossary, URI> rootTerms;
}
