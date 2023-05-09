package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.Identifier;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;

import java.net.URI;
import java.time.Instant;

@StaticMetamodel(UserAccount.class)
public abstract class UserAccount_ {

    public static volatile Identifier<UserAccount, URI> uri;

    public static volatile SingularAttribute<UserAccount, String> firstName;

    public static volatile SingularAttribute<UserAccount, String> lastName;

    public static volatile SingularAttribute<UserAccount, String> username;

    public static volatile SingularAttribute<UserAccount, String> password;

    public static volatile SingularAttribute<UserAccount, Instant> lastSeen;

    public static volatile TypesSpecification<UserAccount, String> types;
}
