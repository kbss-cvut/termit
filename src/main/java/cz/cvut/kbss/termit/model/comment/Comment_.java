package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.AbstractEntity_;
import cz.cvut.kbss.termit.model.User;

import java.net.URI;
import java.time.Instant;

@StaticMetamodel(Comment.class)
public abstract class Comment_ extends AbstractEntity_ {

    public static volatile SingularAttribute<Comment, URI> asset;

    public static volatile SingularAttribute<Comment, String> content;

    public static volatile SingularAttribute<Comment, User> author;

    public static volatile SingularAttribute<Comment, Instant> created;

    public static volatile SingularAttribute<Comment, Instant> modified;

    public static volatile SetAttribute<Comment, CommentReaction> reactions;
}
