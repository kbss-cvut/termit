package cz.cvut.kbss.termit.persistence.dao.util;

import com.github.sgov.server.ValidationResultSeverityComparator;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.turtle.TurtleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Validator {

    private static final Logger LOG = LoggerFactory.getLogger(Validator.class);

    private final org.eclipse.rdf4j.repository.Repository repository;
    private final ValueFactory vf;

    @Autowired
    public Validator(EntityManager em) {
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        vf = repository.getValueFactory();
    }

    private Model getModelFromRdf4jRepository(final Collection<URI> vocabularyIris)
        throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(baos);
        final RepositoryConnection c = repository.getConnection();
        final List<IRI> iris = new ArrayList<>();
        vocabularyIris.forEach(i -> iris.add(vf.createIRI(i.toString())));
        c.export(new TurtleWriter(writer), iris.toArray(new IRI[] {}));
        writer.close();
        final byte[] savedData = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(savedData);
        org.apache.jena.rdf.model.Model model = ModelFactory.createDefaultModel();
        model.read(bais, null, "TURTLE");
        return model;
    }

    @Transactional
    public List<ValidationResult> validate(final Collection<URI> vocabularyIris)
        throws IOException {
        LOG.info("Validating");
        final com.github.sgov.server.Validator validator = new com.github.sgov.server.Validator();
        final Set<URL> rules = new HashSet<>();
        rules.addAll(
            validator.getGlossaryRules());
        rules.addAll(validator.getModelRules());
        LOG.info("Validation model created");

        final Model model = getModelFromRdf4jRepository(vocabularyIris);

        LOG.info("Data model created");
        org.topbraid.shacl.validation.ValidationReport report = validator.validate(model, rules);
        LOG.info("Validation finished");

        return report.results().stream()
            .sorted(new ValidationResultSeverityComparator()).map(result -> {
                final URI termUri = URI.create(result.getFocusNode().toString());
                final URI severity = URI.create(result.getSeverity().getURI());
                final URI errorUri = result.getSourceShape().isURIResource() ?
                    URI.create(result.getSourceShape().getURI()) : null;
                final MultilingualString messages = new MultilingualString(result.getMessages().stream()
                    .map(m -> m.asLiteral())
                    .collect(Collectors.toMap(Literal::getLanguage, Literal::getLexicalForm)));

                LOG.info("'{}' for {} with severity {} and rule {}",
                    severity, termUri, result.getMessage(), errorUri);
                return new ValidationResult()
                    .setTermUri(termUri)
                    .setIssueCauseUri(errorUri)
                    .setMessage(messages)
                    .setSeverity(severity);
            }).collect(Collectors.toList());
    }
}
