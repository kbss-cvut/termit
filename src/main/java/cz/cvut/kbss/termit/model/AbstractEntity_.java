package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.Identifier;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

import java.net.URI;

@StaticMetamodel(AbstractEntity.class)
public abstract class AbstractEntity_ {

    public static volatile Identifier<AbstractEntity, URI> uri;
}
