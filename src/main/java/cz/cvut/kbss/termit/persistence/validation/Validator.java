package cz.cvut.kbss.termit.persistence.validation;

import com.github.sgov.server.ValidationResultSeverityComparator;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.FileUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.turtle.TurtleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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

    /**
     * TermIt overrides some glossary validation rules predefined by SGoV.
     * <p>
     * The main reason for overriding is missing internationalization of the built-in rules.
     */
    private static final Set<String> GLOSSARY_RULES_TO_OVERRIDE = Set.of("g2.ttl", "g4.ttl");

    /**
     * Model validation rules to add.
     * <p>
     * TermIt does not support all the modeling features validated by SGoV validator. So we use only those we support.
     */
    private static final Set<String> MODEL_RULES_TO_ADD = Set.of("m1.ttl", "m2.ttl");

    private final org.eclipse.rdf4j.repository.Repository repository;
    private final ValueFactory vf;

    private final String language;

    @Autowired
    public Validator(EntityManager em, Configuration config) {
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        vf = repository.getValueFactory();
        this.language = config.getPersistence().getLanguage();
    }

    private Model getModelFromRdf4jRepository(final Collection<URI> vocabularyIris)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        final RepositoryConnection c = repository.getConnection();
        final List<IRI> iris = new ArrayList<>();
        vocabularyIris.forEach(i -> iris.add(vf.createIRI(i.toString())));
        c.export(new TurtleWriter(writer), iris.toArray(new IRI[]{}));
        writer.close();
        final byte[] savedData = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(savedData);
        Model model = ModelFactory.createDefaultModel();
        model.read(bais, null, FileUtils.langTurtle);
        return model;
    }

    @Transactional(readOnly = true)
    @Override
    public List<ValidationResult> validate(final Collection<URI> vocabularyIris) {
        LOG.debug("Validating {}", vocabularyIris);
        final com.github.sgov.server.Validator validator = new com.github.sgov.server.Validator();
        try {
            final Model validationModel = initValidationModel(validator);
            final Model dataModel = getModelFromRdf4jRepository(vocabularyIris);
            org.topbraid.shacl.validation.ValidationReport report = validator.validate(dataModel, validationModel);
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
                                                                                                 Literal::getLanguage,
                                                                                                 Literal::getLexicalForm)));

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

    private Model initValidationModel(com.github.sgov.server.Validator validator) throws IOException {
        // TODO We could cache the validation model between calls
        final Set<URL> rules = new HashSet<>();
        rules.addAll(validator.getGlossaryRules().stream()
                              .filter(r -> GLOSSARY_RULES_TO_OVERRIDE.stream().noneMatch(s -> r.toString().contains(s)))
                              .collect(Collectors.toSet()));
        rules.addAll(
                // Currently, only using content rules, not OntoUml, as TermIt does not support adding OntoUml rules
                validator.getModelRules().stream()
                         .filter(r -> MODEL_RULES_TO_ADD.stream().anyMatch(s -> r.toString().contains(s)))
                         .collect(Collectors.toList())
        );
        final Model validationModel = com.github.sgov.server.Validator.getRulesModel(rules);
        loadOverrideRules(validationModel);
        return validationModel;
    }

    private void loadOverrideRules(Model validationModel) throws IOException {
        final ClassLoader classLoader = Validator.class.getClassLoader();
        final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);

        Resource[] resources = resolver.getResources("classpath:/validation/*.ttl");
        for (Resource r : resources) {
            String rule = Utils.loadClasspathResource("validation/" + r.getFilename());
            rule = rule.replace("$lang", language);
            validationModel.read(new ByteArrayInputStream(rule.getBytes(StandardCharsets.UTF_8)), null,
                                 FileUtils.langTurtle);
        }
    }
}
