package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.Identifier;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;

import java.net.URI;

@StaticMetamodel(TermInfo.class)
public abstract class TermInfo_ {

    public static volatile Identifier<TermInfo, URI> uri;

    public static volatile SingularAttribute<TermInfo, MultilingualString> label;

    public static volatile SingularAttribute<TermInfo, URI> vocabulary;

    public static volatile TypesSpecification<TermInfo, String> types;
}
