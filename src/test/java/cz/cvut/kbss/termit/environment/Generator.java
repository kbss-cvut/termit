/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.environment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Model;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserGroupAccessControlRecord;
import cz.cvut.kbss.termit.model.assignment.DefinitionalOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionalOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Utils;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Generator {

    public static URI[] TERM_STATES = new URI[]{
            URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/new-term"),
            URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/published-term"),
            URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/cancelled-term")
    };

    private static final Random random = new Random();

    private Generator() {
        throw new AssertionError();
    }

    /**
     * Generates a (pseudo) random URI.
     *
     * @return Random URI
     */
    public static URI generateUri() {
        return URI.create(Environment.BASE_URI + "/randomInstance" + randomInt());
    }

    /**
     * Generates a (pseudo) random URI and returns it as a string.
     *
     * @return Random URI string
     */
    public static String generateUriString() {
        return generateUri().toString();
    }

    /**
     * Generates a (pseudo-)random integer between the specified lower and upper bounds.
     *
     * @param lowerBound Lower bound, inclusive
     * @param upperBound Upper bound, exclusive
     * @return Randomly generated integer
     */
    public static int randomInt(int lowerBound, int upperBound) {
        int rand;
        do {
            rand = random.nextInt(upperBound);
        } while (rand < lowerBound);
        return rand;
    }

    /**
     * Generates a (pseudo) random integer.
     * <p>
     * This version has no bounds (aside from the integer range), so the returned number may be negative or zero.
     *
     * @return Randomly generated integer
     * @see #randomInt(int, int)
     */
    public static int randomInt() {
        return random.nextInt();
    }

    /**
     * Generates a (pseudo)random index of an element in the collection.
     * <p>
     * I.e. the returned number is in the interval <0, col.size()).
     *
     * @param col The collection
     * @return Random index
     */
    public static int randomIndex(Collection<?> col) {
        assert col != null;
        assert !col.isEmpty();
        return random.nextInt(col.size());
    }

    /**
     * Generates a (pseudo)random index of an element in the array.
     * <p>
     * I.e. the returned number is in the interval <0, arr.length).
     *
     * @param arr The array
     * @return Random index
     */
    public static int randomIndex(Object[] arr) {
        assert arr != null;
        assert arr.length > 0;
        return random.nextInt(arr.length);
    }

    /**
     * Returns a (pseudo)random element from the specified array.
     *
     * @param arr Array to select random element from
     * @param <T> Element type
     * @return Random array element
     */
    public static <T> T randomItem(T[] arr) {
        return arr[randomIndex(arr)];
    }

    /**
     * Returns a (pseudo)random element from the specified list.
     * @param lst List to select random element from
     * @return Element from the list
     * @param <T> Element type
     */
    public static <T> T randomElement(List<T> lst) {
        return lst.get(randomIndex(lst));
    }

    /**
     * Generators a (pseudo) random boolean.
     *
     * @return Random boolean
     */
    public static boolean randomBoolean() {
        return random.nextBoolean();
    }

    /**
     * Creates a random instance of {@link User}.
     * <p>
     * The instance has no identifier set.
     *
     * @return New {@code User} instance
     * @see #generateUserWithId()
     */
    public static User generateUser() {
        final User user = new User();
        user.setFirstName("Firstname" + randomInt());
        user.setLastName("Lastname" + randomInt());
        user.setUsername("user" + randomInt() + "@kbss.felk.cvut.cz");
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType());
        return user;
    }

    /**
     * Creates a random instance of {@link User} with a generated identifier.
     * <p>
     * The presence of identifier is the only difference between this method and {@link #generateUser()}.
     *
     * @return New {@code User} instance
     */
    public static User generateUserWithId() {
        final User user = generateUser();
        user.setUri(Generator.generateUri());
        return user;
    }

    /**
     * Generates a random {@link UserAccount} instance, initialized with first name, last name, username and
     * identifier.
     *
     * @return A new {@code UserAccount} instance
     */
    public static UserAccount generateUserAccount() {
        final UserAccount account = new UserAccount();
        account.setFirstName("FirstName" + randomInt());
        account.setLastName("LastName" + randomInt());
        account.setUsername("user" + randomInt() + "@kbss.felk.cvut.cz");
        account.setUri(Generator.generateUri());
        return account;
    }

    /**
     * Generates a random {@link UserAccount} instance, initialized with first name, last name, username, password and
     * identifier.
     *
     * @return A new {@code UserAccount} instance
     * @see #generateUserAccount()
     */
    public static UserAccount generateUserAccountWithPassword() {
        final UserAccount account = generateUserAccount();
        account.setPassword("Pass" + randomInt(0, 10000));
        return account;
    }

    /**
     * Generates a {@link cz.cvut.kbss.termit.model.Vocabulary} instance with a name, an empty glossary and a model.
     *
     * @return New {@code Vocabulary} instance
     */
    public static cz.cvut.kbss.termit.model.Vocabulary generateVocabulary() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary =
                new cz.cvut.kbss.termit.model.Vocabulary();
        vocabulary.setGlossary(new Glossary());
        vocabulary.setModel(new Model());
        vocabulary.setLabel(MultilingualString.create("Vocabulary" + randomInt(), Environment.LANGUAGE));
        vocabulary.setDescription(MultilingualString.create(
                "Description of vocabulary " + vocabulary.getLabel().get(Environment.LANGUAGE), Environment.LANGUAGE));
        return vocabulary;
    }

    public static cz.cvut.kbss.termit.model.Vocabulary generateVocabularyWithId() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        return vocabulary;
    }

    public static Term generateMultiLingualTerm(String... languages) {
        final Term term = new Term();
        term.setUri(Generator.generateUri());
        final MultilingualString label = new MultilingualString();
        final MultilingualString definition = new MultilingualString();
        final MultilingualString description = new MultilingualString();
        int id = randomInt();
        Arrays.stream(languages).forEach(language -> {
            label.set(language, "Term-" + language + "-" + id);
            definition.set(language, "Normative definition of term " + language + "-" + id);
            description.set(language, "Normative description of term " + language + "-" + id);
        });
        term.setLabel(label);
        term.setDefinition(definition);
        term.setDescription(description);
        return term;
    }

    public static Term generateTerm() {
        final Term term = new Term();
        term.setLabel(MultilingualString.create("Term" + randomInt(), Environment.LANGUAGE));
        term.setDefinition(MultilingualString
                                   .create("Normative definition of term " + term.getLabel().get(Environment.LANGUAGE),
                                           Environment.LANGUAGE));
        term.setDescription(MultilingualString.create("Comment" + randomInt(), Environment.LANGUAGE));
        if (Generator.randomBoolean()) {
            term.setSources(Collections.singleton("PSP/c-1/p-2/b-c"));
        }
        return term;
    }

    public static Term generateTermWithId() {
        final Term term = generateTerm();
        term.setUri(Generator.generateUri());
        return term;
    }

    public static TermInfo generateTermInfoWithId() {
        final TermInfo term = new TermInfo();
        term.setUri(Generator.generateUri());
        term.setLabel(MultilingualString.create("Term" + randomInt(), Environment.LANGUAGE));
        return term;
    }

    public static Term generateTermWithId(URI vocabularyUri) {
        final Term t = generateTermWithId();
        t.setVocabulary(vocabularyUri);
        return t;
    }

    public static List<Term> generateTermsWithIds(int count) {
        return IntStream.range(0, count).mapToObj(i -> generateTermWithId())
                        .collect(Collectors.toList());
    }

    public static Resource generateResource() {
        final Resource resource = new Resource();
        resource.setLabel("Resource " + randomInt());
        resource.setDescription("Resource description ");
        return resource;
    }

    public static Resource generateResourceWithId() {
        final Resource resource = generateResource();
        resource.setUri(Generator.generateUri());
        return resource;
    }

    public static Document generateDocumentWithId() {
        final Document document = new Document();
        document.setLabel("Document " + randomInt());
        document.setDescription("Document description");
        document.setUri(generateUri());
        return document;
    }

    public static File generateFileWithId(String fileName) {
        final File file = new File();
        file.setLabel(fileName);
        file.setUri(Generator.generateUri());
        return file;
    }

    public static PersistChangeRecord generatePersistChange(Asset<?> asset) {
        final PersistChangeRecord record = new PersistChangeRecord(asset);
        record.setTimestamp(Utils.timestamp());
        if (Environment.getCurrentUser() != null) {
            record.setAuthor(Environment.getCurrentUser().toUser());
        }
        return record;
    }

    /**
     * Generates a change record indicating change of the specified asset's label from nothing to the current value.
     *
     * @param asset Changed asset
     * @return Change record
     */
    public static UpdateChangeRecord generateUpdateChange(Asset<?> asset) {
        final UpdateChangeRecord record = new UpdateChangeRecord(asset);
        record.setTimestamp(Utils.timestamp());
        if (Environment.getCurrentUser() != null) {
            record.setAuthor(Environment.getCurrentUser().toUser());
        }
        record.setChangedAttribute(URI.create(RDFS.LABEL));
        record.setNewValue(Collections.singleton(asset.getLabel()));
        return record;
    }

    /**
     * Generates a list of change records for the specified asset.
     * <p>
     * The list contains one persist record and several update records.
     *
     * @param asset Asset to generate change records for
     * @param user  Author of the changes
     * @return List of change records
     */
    public static List<AbstractChangeRecord> generateChangeRecords(Asset<?> asset, User user) {
        final PersistChangeRecord persistRecord = generatePersistChange(asset);
        final List<AbstractChangeRecord> result =
                IntStream.range(0, 5).mapToObj(i -> generateUpdateChange(asset))
                         .collect(Collectors.toList());
        result.add(0, persistRecord);
        if (user != null) {
            result.forEach(r -> r.setAuthor(user));
        }
        return result;
    }

    /**
     * Simulates inference of the "je-pojmem-ze-slovniku" relationship between a term and its vocabulary.
     *
     * @param term          Term in vocabulary
     * @param vocabularyIri Vocabulary identifier
     * @param em            Transactional entity manager to unwrap repository connection from
     */
    public static void addTermInVocabularyRelationship(Term term, URI vocabularyIri,
                                                       EntityManager em) {
        final Repository repo = em.unwrap(Repository.class);
        try (RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.add(vf.createIRI(term.getUri().toString()),
                     vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku),
                     vf.createIRI(vocabularyIri.toString()));
        }
    }

    /**
     * Generates a random {@link cz.cvut.kbss.termit.model.comment.Comment} instance, initialized with the term it is
     * connected to.
     *
     * @return A new {@code Comment} instance
     */
    public static Comment generateComment(User user, Asset<?> asset) {
        final Comment comment = new Comment();
        if (asset != null) {
            comment.setAsset(asset.getUri());
        }
        comment.setAuthor(user);
        comment.setCreated(Utils.timestamp());
        comment.setModified(Utils.timestamp());
        comment.setContent("Comment " + randomInt());
        comment.setUri(Generator.generateUri());
        return comment;
    }

    /**
     * Generates a list of five comments for the given term.
     *
     * @param term term to generate comments for, or null if the comments should not be assigned to any term.
     * @return a list of comments
     */
    public static List<Comment> generateComments(Term term) {
        return IntStream.range(0, 5).mapToObj(i -> generateComment(null, term)).collect(Collectors.toList());
    }

    public static TermOccurrence generateTermOccurrence(Term term, Asset<?> target, boolean suggested) {
        final TermOccurrence occurrence;
        if (target instanceof File) {
            occurrence = new TermFileOccurrence(term.getUri(), new FileOccurrenceTarget((File) target));
        } else {
            assert target instanceof Term;
            occurrence = new TermDefinitionalOccurrence(term.getUri(), new DefinitionalOccurrenceTarget((Term) target));
        }
        if (suggested) {
            occurrence.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_navrzeny_vyskyt_termu);
        }
        // Dummy selector
        occurrence.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test text")));
        return occurrence;
    }

    public static Snapshot generateSnapshot(Asset<?> asset) {
        final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        final URI uri = URI.create(
                asset.getUri().toString() + "/version/" + timestamp.toString().replace(":", "").replace(" ", ""));
        final String type;
        if (asset instanceof Vocabulary) {
            type = cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku;
        } else if (asset instanceof AbstractTerm) {
            type = cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu;
        } else {
            type = cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_objektu;
        }
        return new Snapshot(uri, timestamp, asset.getUri(), type);
    }

    public static void simulateInferredSkosRelationship(AbstractTerm source, Collection<? extends AbstractTerm> related,
                                                        String relationship, EntityManager em) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.begin();
            for (AbstractTerm r : related) {
                // Don't put it into any specific context to make it look like inference
                conn.add(vf.createIRI(r.getUri().toString()), vf.createIRI(relationship),
                         vf.createIRI(source.getUri().toString()));
            }
            conn.commit();
        }
    }

    public static UserGroup generateUserGroup() {
        final UserGroup group = new UserGroup();
        group.setUri(
                IdentifierResolver.generateSyntheticIdentifier(cz.cvut.kbss.termit.util.Vocabulary.s_c_sioc_Usergroup));
        group.setLabel(UserGroup.class.getSimpleName() + Generator.randomInt());
        return group;
    }

    public static AccessControlList generateAccessControlList(boolean withRecords) {
        final AccessControlList acl = new AccessControlList();
        acl.setUri(Generator.generateUri());
        if (withRecords) {
            acl.setRecords(new HashSet<>(generateAccessControlRecords()));
        }
        return acl;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<AccessControlRecord<?>> generateAccessControlRecords() {
        final List<AccessControlRecord<?>> result = IntStream.range(0, 5).mapToObj(i -> {
            final AccessControlRecord r;
            int maxAccessLevel = AccessLevel.values().length; // exclusive
            if (Generator.randomBoolean()) {
                r = new UserAccessControlRecord();
                r.setHolder(Generator.generateUserWithId());
            } else {
                r = new UserGroupAccessControlRecord();
                r.setHolder(generateUserGroup());
                maxAccessLevel = AccessLevel.SECURITY.ordinal();
            }
            r.setUri(Generator.generateUri());
            r.setAccessLevel(AccessLevel.values()[Generator.randomInt(0, maxAccessLevel)]);
            return (AccessControlRecord<?>) r;
        }).collect(Collectors.toList());

        final RoleAccessControlRecord fr = new RoleAccessControlRecord();
        final UserRole fullRole = new UserRole(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER);
        fr.setHolder(fullRole);
        fr.setAccessLevel(AccessLevel.NONE);
        result.add(fr);

        final RoleAccessControlRecord rr = new RoleAccessControlRecord();
        final UserRole restrictedRole = new UserRole(cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER);
        rr.setHolder(restrictedRole);
        rr.setAccessLevel(AccessLevel.NONE);
        result.add(rr);

        final RoleAccessControlRecord ar = new RoleAccessControlRecord();
        final UserRole anonymousRole = new UserRole(cz.cvut.kbss.termit.security.model.UserRole.ANONYMOUS_USER);
        ar.setHolder(anonymousRole);
        ar.setAccessLevel(AccessLevel.NONE);
        result.add(ar);

        return result;
    }
}
