package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;
import cz.cvut.kbss.termit.model.AbstractEntity_;

import java.net.URI;

@StaticMetamodel(CommentReaction.class)
public abstract class CommentReaction_ extends AbstractEntity_ {

    public static volatile SingularAttribute<CommentReaction, URI> actor;

    public static volatile SingularAttribute<CommentReaction, URI> object;

    public static volatile TypesSpecification<CommentReaction, String> types;
}
