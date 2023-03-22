package cz.cvut.kbss.termit.persistence.validation;

import com.github.sgov.server.ValidationResultSeverityComparator;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.turtle.TurtleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Validator implements VocabularyContentValidator {

    private static final Logger LOG = LoggerFactory.getLogger(Validator.class);

    private final org.eclipse.rdf4j.repository.Repository repository;
    private final ValueFactory vf;

    private final VocabularyContextMapper vocabularyContextMapper;

    @Autowired
    public Validator(EntityManager em, VocabularyContextMapper vocabularyContextMapper) {
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        this.vocabularyContextMapper = vocabularyContextMapper;
        vf = repository.getValueFactory();
    }

    private Model getModelFromRdf4jRepository(final Collection<URI> vocabularyIris)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        final RepositoryConnection c = repository.getConnection();
        final List<IRI> iris = new ArrayList<>();
        vocabularyIris.forEach(i -> iris.add(vf.createIRI(vocabularyContextMapper.getVocabularyContext(i).toString())));
        c.export(new TurtleWriter(writer), iris.toArray(new IRI[]{}));
        writer.close();
        final byte[] savedData = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(savedData);
        org.apache.jena.rdf.model.Model model = ModelFactory.createDefaultModel();
        model.read(bais, null, "TURTLE");
        return model;
    }

    @Transactional(readOnly = true)
    @Override
    public List<ValidationResult> validate(final Collection<URI> vocabularyIris) {
        LOG.debug("Validating {}", vocabularyIris);
        final com.github.sgov.server.Validator validator = new com.github.sgov.server.Validator();
        final Set<URL> rules = new HashSet<>();
        rules.addAll(validator.getGlossaryRules());
        rules.addAll(
                // Currently, only using content rules, not OntoUml, as TermIt does not support adding OntoUml rules
                validator.getModelRules().stream().filter(r ->
                        r.toString().contains("m1.ttl") || r.toString().contains("m2.ttl"))
                        .collect(Collectors.toList())
        );

        try {
            final Model model = getModelFromRdf4jRepository(vocabularyIris);
            org.topbraid.shacl.validation.ValidationReport report = validator.validate(model, rules);
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
                                .collect(Collectors.toMap(Literal::getLanguage, Literal::getLexicalForm)));

                        return new ValidationResult()
                                .setTermUri(termUri)
                                .setIssueCauseUri(errorUri)
                                .setMessage(messages)
                                .setSeverity(severity)
                                .setResultPath(resultPath);
                    }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new TermItException("Validation of vocabularies " + vocabularyIris + " failed.", e);
        }
    }
}
