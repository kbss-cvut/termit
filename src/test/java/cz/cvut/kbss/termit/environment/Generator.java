/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cz.cvut.kbss.termit.environment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.topbraid.shacl.vocabulary.SH;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Generator {

    private static final Random random = new Random();

    private Generator() {
        throw new AssertionError();
    }

    /**
     * Generates a (pseudo) random URI, usable for test individuals.
     *
     * @return Random URI
     */
    public static URI generateUri() {
        return URI.create(Environment.BASE_URI + "/randomInstance" + randomInt());
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
     * This version has no bounds (aside from the integer range), so the returned number may be
     * negative or zero.
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
        return user;
    }

    /**
     * Creates a random instance of {@link User} with a generated identifier.
     * <p>
     * The presence of identifier is the only difference between this method and
     * {@link #generateUser()}.
     *
     * @return New {@code User} instance
     */
    public static User generateUserWithId() {
        final User user = generateUser();
        user.setUri(Generator.generateUri());
        return user;
    }

    /**
     * Generates a random {@link UserAccount} instance, initialized with first name, last name,
     * username and
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
     * Generates a random {@link UserAccount} instance, initialized with first name, last name,
     * username, password and
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
     * Generates a {@link cz.cvut.kbss.termit.model.Vocabulary} instance with a name, an empty
     * glossary and a model.
     *
     * @return New {@code Vocabulary} instance
     */
    public static cz.cvut.kbss.termit.model.Vocabulary generateVocabulary() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary =
                new cz.cvut.kbss.termit.model.Vocabulary();
        vocabulary.setGlossary(new Glossary());
        vocabulary.setModel(new Model());
        vocabulary.setLabel("Vocabulary" + randomInt());
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
        term.setLabel(MultilingualString.create("Term" + randomInt(), Constants.DEFAULT_LANGUAGE));
        term.setDefinition(MultilingualString
                .create("Normative definition of term " + term.getLabel().get(),
                        Constants.DEFAULT_LANGUAGE));
        term.setDescription(MultilingualString.create("Comment" + randomInt(), Constants.DEFAULT_LANGUAGE));
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
        term.setLabel(MultilingualString.create("Term" + randomInt(), Constants.DEFAULT_LANGUAGE));
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

    public static Target generateTargetWithId() {
        final Target target = new Target();
        target.setUri(Generator.generateUri());
        return target;
    }

    public static TermAssignment generateTermAssignmentWithId() {
        final TermAssignment termAssignment = new TermAssignment();
        termAssignment.setUri(Generator.generateUri());
        return termAssignment;
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
        record.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (Environment.getCurrentUser() != null) {
            record.setAuthor(Environment.getCurrentUser().toUser());
        }
        return record;
    }

    /**
     * Generates a change record indicating change of the specified asset's label from nothing to
     * the current value.
     *
     * @param asset Changed asset
     * @return Change record
     */
    public static UpdateChangeRecord generateUpdateChange(Asset<?> asset) {
        final UpdateChangeRecord record = new UpdateChangeRecord(asset);
        record.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (Environment.getCurrentUser() != null) {
            record.setAuthor(Environment.getCurrentUser().toUser());
        }
        record.setChangedAttribute(URI.create(RDFS.LABEL));
        record.setNewValue(Collections.singleton(asset.getLabel()));
        return record;
    }

    public static List<AbstractChangeRecord> generateChangeRecords(Asset<?> asset, User user) {
        final PersistChangeRecord persistRecord = generatePersistChange(asset);
        final List<AbstractChangeRecord> result =
                IntStream.range(0, 5).mapToObj(i -> generateUpdateChange(asset))
                        .collect(
                                Collectors.toList());
        result.add(0, persistRecord);
        if (user != null) {
            result.forEach(r -> r.setAuthor(user));
        }
        return result;
    }

    public static List<cz.cvut.kbss.termit.model.validation.ValidationResult> generateValidationRecords() {
        return IntStream.range(0, 1)
                .mapToObj(i ->
                        new cz.cvut.kbss.termit.model.validation.ValidationResult()
                                .setTermUri(URI.create("https://example.org/term-" + i))
                                .setIssueCauseUri(URI.create("https://example.org/issue-" + i))
                                .setSeverity(URI.create(SH.Violation.toString()))
                )
                .collect(Collectors.toList());
    }

    /**
     * Simulates inference of the "je-pojmem-ze-slovniku" relationship between a term and its
     * vocabulary.
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
     * Generates a random {@link cz.cvut.kbss.termit.model.comment.Comment} instance, initialized
     * with the term it is connected to.
     *
     * @return A new {@code Comment} instance
     */
    public static Comment generateComment(User user, Asset asset) {
        final Comment comment = new Comment();
        comment.setAsset(asset.getUri());
        comment.setAuthor(user);
        comment.setCreated(new Date());
        comment.setModified(new Date());
        comment.setContent("Comment " + randomInt());
        comment.setUri(Generator.generateUri());
        return comment;
    }
}
