package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.resource.Resource;

import java.net.URI;
import java.time.Instant;

@StaticMetamodel(TextAnalysisRecord.class)
public abstract class TextAnalysisRecord_ extends AbstractEntity_ {

    public static volatile SingularAttribute<TextAnalysisRecord, Instant> date;

    public static volatile SingularAttribute<TextAnalysisRecord, Resource> analyzedResource;

    public static volatile SetAttribute<TextAnalysisRecord, URI> vocabularies;
}
