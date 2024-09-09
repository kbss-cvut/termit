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
package cz.cvut.kbss.termit.persistence.validation;

import com.github.sgov.server.ValidationResultSeverityComparator;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.event.VocabularyValidationFinishedEvent;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
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

    private final EntityManager em;
    private final VocabularyContextMapper vocabularyContextMapper;
    private final ApplicationEventPublisher eventPublisher;

    private Model validationModel;

    @Autowired
    public Validator(EntityManager em,
                     VocabularyContextMapper vocabularyContextMapper,
                     Configuration config, ApplicationEventPublisher eventPublisher) {
        this.em = em;
        this.vocabularyContextMapper = vocabularyContextMapper;
        this.eventPublisher = eventPublisher;
        initValidator(config.getPersistence().getLanguage());
    }

    /**
     * Initializes the validator.
     * <p>
     * Note that the validator can be reused as long as the language stays the same. If TermIt starts supporting
     * selection of language per vocabulary, this initialization will have to change.
     *
     * @param language Primary language of the instance, used to parameterize validation rules
     */
    private void initValidator(String language) {
        try {
            this.validationModel = initValidationModel(new com.github.sgov.server.Validator(), language);
        } catch (IOException e) {
            throw new TermItException("Unable to initialize validator.", e);
        }
    }

    private Model initValidationModel(com.github.sgov.server.Validator validator, String language) throws IOException {
        final Set<URL> rules = new HashSet<>();
        rules.addAll(validator.getGlossaryRules().stream()
                              .filter(r -> GLOSSARY_RULES_TO_OVERRIDE.stream().noneMatch(s -> r.toString().contains(s)))
                              .collect(Collectors.toSet()));
        rules.addAll(
                // Currently, only using content rules, not OntoUml, as TermIt does not support adding OntoUml rules
                validator.getModelRules().stream()
                         .filter(r -> MODEL_RULES_TO_ADD.stream().anyMatch(s -> r.toString().contains(s)))
                         .toList()
        );
        final Model validationModel = com.github.sgov.server.Validator.getRulesModel(rules);
        loadOverrideRules(validationModel, language);
        return validationModel;
    }

    private void loadOverrideRules(Model validationModel, String language) throws IOException {
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

    @Throttle("{#originVocabularyIri}")
    @Transactional(readOnly = true)
    @Override
    public @NotNull ThrottledFuture<Collection<ValidationResult>> validate(final @NotNull URI originVocabularyIri, final @NotNull Collection<URI> vocabularyIris) {
        if (vocabularyIris.isEmpty()) {
            return ThrottledFuture.done(List.of());
        }

        return ThrottledFuture.of(() -> {
            final List<ValidationResult> results = runValidation(vocabularyIris);
            eventPublisher.publishEvent(new VocabularyValidationFinishedEvent(this, originVocabularyIri, vocabularyIris, results));
            return results;
        });
    }

    protected synchronized List<ValidationResult> runValidation(@NotNull Collection<URI> vocabularyIris) {
        LOG.debug("Validating {}", vocabularyIris);
        try {
            LOG.trace("Constructing model from RDF4J repository...");
            final Model dataModel = getModelFromRdf4jRepository(vocabularyIris);
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
                                                                                                 lit -> lit.getLanguage().isBlank() ?
                                                                                                         JsonLd.NONE : lit.getLanguage(),
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

    private Model getModelFromRdf4jRepository(final Collection<URI> vocabularyIris)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        final Repository repository = em.unwrap(Repository.class);
        final ValueFactory vf = repository.getValueFactory();
        try (final RepositoryConnection c = repository.getConnection()) {
            final List<IRI> iris = new ArrayList<>();
            vocabularyIris.forEach(
                    i -> iris.add(vf.createIRI(vocabularyContextMapper.getVocabularyContext(i).toString())));
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
