package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.Identifier;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

import java.net.URI;

@StaticMetamodel(Asset.class)
public abstract class Asset_ {

    public static volatile Identifier<Asset, URI> uri;
}
