package cz.cvut.kbss.termit.persistence.validation;

import com.github.sgov.server.Rule;
import com.github.sgov.server.ValidationResultSeverityComparator;
import com.github.sgov.server.ValidationRules;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import jakarta.annotation.Nonnull;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.FileUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.turtle.TurtleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Va
 */
public class LocalValidator implements RepositoryContextValidator {

    private static final Logger LOG = LoggerFactory.getLogger(LocalValidator.class);

    /**
     * Model validation rules to add.
     * <p>
     * TermIt does not support all the modeling features validated by SGoV validator. So we use only those we support.
     */
    private static final Set<String> MODEL_RULES_TO_ADD = Set.of("m1.ttl", "m2.ttl");

    private final EntityManager em;

    public LocalValidator(EntityManager em) {
        this.em = em;
    }

    private static Model initValidationModel(String language) throws IOException {
        final Set<Rule> rules = new HashSet<>(ValidationRules.glossaryRules(language));
        rules.addAll(
                // Currently, only using content rules, not OntoUml, as TermIt does not support adding OntoUml rules
                ValidationRules.modelRules(language).stream()
                               .filter(r -> MODEL_RULES_TO_ADD.stream().anyMatch(s -> r.toString().contains(s)))
                               .toList()
        );
        final Model validationModel = ModelFactory.createDefaultModel();
        rules.forEach(
                r -> validationModel.read(new ByteArrayInputStream(r.content().getBytes(StandardCharsets.UTF_8)), null,
                                          FileUtils.langTurtle));
        return validationModel;
    }

    @Transactional(readOnly = true)
    @Nonnull
    public List<ValidationResult> validate(@Nonnull List<URI> contexts, @Nonnull String language) {
        Objects.requireNonNull(contexts);
        Objects.requireNonNull(language);
        assert !contexts.isEmpty();

        LOG.trace("Running local validation of contexts {}", contexts);
        try {
            LOG.trace("Constructing model from RDF4J repository...");
            final Model dataModel = getModelFromRdf4jRepository(contexts);
            final Model validationModel = initValidationModel(language);
            LOG.trace("Model constructed, running validation...");
            org.topbraid.shacl.validation.ValidationReport report = new com.github.sgov.server.Validator()
                    .validate(dataModel, validationModel);
            LOG.debug("Done.");
            return report.results().stream()
                         .sorted(new ValidationResultSeverityComparator()).map(result -> {
                        final URI termUri = URI.create(result.getFocusNode().toString());
                        final URI severity = URI.create(result.getSeverity().getURI());
                        final URI errorUri = result.getSourceShape().isURIResource() ?
                                             URI.create(result.getSourceShape().getURI()) : null;
                        final URI resultPath = result.getPath() != null && result.getPath().isURIResource() ?
                                               URI.create(result.getPath().getURI()) : null;
                        final MultilingualString messages = new MultilingualString(result.getMessages().stream()
                                                                                         .map(RDFNode::asLiteral)
                                                                                         .collect(Collectors.toMap(
                                                                                                 lit -> lit.getLanguage()
                                                                                                           .isBlank() ?
                                                                                                        JsonLd.NONE :
                                                                                                        lit.getLanguage(),
                                                                                                 Literal::getLexicalForm)));

                        return new ValidationResult()
                                .setTermUri(termUri)
                                .setIssueCauseUri(errorUri)
                                .setMessage(messages)
                                .setSeverity(severity)
                                .setResultPath(resultPath);
                    }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new TermItException("Validation of contexts " + contexts + " failed.", e);
        }
    }

    private Model getModelFromRdf4jRepository(final Collection<URI> contexts)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        final Repository repository = em.unwrap(Repository.class);
        final ValueFactory vf = repository.getValueFactory();
        try (final RepositoryConnection c = repository.getConnection()) {
            final List<IRI> iris = new ArrayList<>();
            contexts.forEach(i -> iris.add(vf.createIRI(i.toString())));
            c.export(new TurtleWriter(writer), iris.toArray(new IRI[]{}));
            writer.close();
        }
        final byte[] savedData = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(savedData);
        Model model = ModelFactory.createDefaultModel();
        model.read(bais, null, FileUtils.langTurtle);
        return model;
    }
}
