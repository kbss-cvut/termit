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
package cz.cvut.kbss.termit.service.document.html;

import cz.cvut.kbss.termit.exception.AnnotationGenerationException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.OccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.service.document.DocumentManager;
import cz.cvut.kbss.termit.service.document.TermOccurrenceResolver;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves term occurrences from RDFa-annotated HTML document.
 * <p>
 * This class is not thread-safe and not re-entrant.
 */
@Service("html")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class HtmlTermOccurrenceResolver extends TermOccurrenceResolver {

    private static final String BNODE_PREFIX = "_:";

    private static final String SCORE_ATTRIBUTE = "score";

    private static final Logger LOG = LoggerFactory.getLogger(HtmlTermOccurrenceResolver.class);

    private final HtmlSelectorGenerators selectorGenerators;

    private final DocumentManager documentManager;

    private final Configuration config;

    private Document document;

    private Asset<?> source;

    private Map<String, String> prefixes;

    private final Set<String> existingTermIds = new HashSet<>();

    @Autowired
    HtmlTermOccurrenceResolver(TermRepositoryService termService, HtmlSelectorGenerators selectorGenerators,
                               DocumentManager documentManager, Configuration config) {
        super(termService);
        this.selectorGenerators = selectorGenerators;
        this.documentManager = documentManager;
        this.config = config;
    }

    @Override
    public void parseContent(InputStream input, Asset<?> source) {
        try {
            this.source = source;
            this.document = Jsoup.parse(input, StandardCharsets.UTF_8.name(), "");
            this.prefixes = resolvePrefixes(document);
        } catch (IOException e) {
            throw new AnnotationGenerationException("Unable to read RDFa document.", e);
        }
    }

    @Override
    public InputStream getContent() {
        assert document != null;
        return new ByteArrayInputStream(document.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> resolvePrefixes(Document document) {
        final Map<String, String> map = new HashMap<>(4);
        final Elements prefixElements = document.getElementsByAttribute(Constants.RDFa.PREFIX);
        prefixElements.forEach(element -> {
            final String prefixStr = element.attr(Constants.RDFa.PREFIX);
            final String[] prefixDefinitions = prefixStr.split("[^:] ");
            for (String def : prefixDefinitions) {
                final String[] split = def.split(": ");
                assert split.length == 2;
                map.put(split[0].trim(), split[1].trim());
            }
        });
        return map;
    }

    private boolean isNotTermOccurrence(Element rdfaElem) {
        if (!rdfaElem.hasAttr(Constants.RDFa.RESOURCE) && !rdfaElem.hasAttr(Constants.RDFa.CONTENT)) {
            return true;
        }
        final String typesString = rdfaElem.attr(Constants.RDFa.TYPE);
        final String[] types = typesString.split(" ");
        // Perhaps we should check also for correct property?
        for (String type : types) {
            final String fullType = fullIri(type);
            if (fullType.equals(cz.cvut.kbss.termit.util.Vocabulary.s_c_vyskyt_termu)) {
                return false;
            }
        }
        return true;
    }

    private String fullIri(String possiblyPrefixed) {
        possiblyPrefixed = possiblyPrefixed.trim();
        final int colonIndex = possiblyPrefixed.indexOf(':');
        if (colonIndex == -1) {
            return possiblyPrefixed;
        }
        final String prefix = possiblyPrefixed.substring(0, colonIndex);
        if (!prefixes.containsKey(prefix)) {
            return possiblyPrefixed;
        }
        final String localName = possiblyPrefixed.substring(colonIndex + 1);
        return prefixes.get(prefix) + localName;
    }

    @Override
    public void findTermOccurrences(OccurrenceConsumer resultConsumer) {
        assert document != null;
        final Set<String> visited = new HashSet<>();
        final Elements elements = document.getElementsByAttribute(Constants.RDFa.ABOUT);
        final Double scoreThreshold = Double.parseDouble(config.getTextAnalysis().getTermOccurrenceMinScore());
        for (Element element : elements) {
            if (isNotTermOccurrence(element)) {
                continue;
            }
            final String about = element.attr(Constants.RDFa.ABOUT);
            if (visited.contains(about)) {
                continue;
            }
            visited.add(about);

            LOG.trace("Processing RDFa annotated element {}.", element);
            final Optional<TermOccurrence> occurrence = resolveAnnotation(element, source);
            occurrence.ifPresent(to -> {
                try {
                    if (!to.isSuggested()) {
                        // Occurrence already approved in content (from previous manual approval)
                        resultConsumer.accept(to);
                    } else if (existsApproved(to)) {
                        LOG.trace("Found term occurrence {} with matching existing approved occurrence.", to);
                        to.markApproved();
                        // Annotation without score is considered approved by the frontend
                        element.removeAttr(SCORE_ATTRIBUTE);
                        resultConsumer.accept(to);
                    } else {
                        if (to.getScore() > scoreThreshold) {
                            LOG.trace("Found term occurrence {}.", to);
                            resultConsumer.accept(to);
                        } else {
                            LOG.trace("The confidence score of occurrence {} is lower than the configured threshold {}.", to, scoreThreshold);
                        }
                    }
                } catch (InterruptedException e) {
                    LOG.error("Thread interrupted while resolving term occurrences.");
                    Thread.currentThread().interrupt();
                    throw new TermItException(e);
                }
            });
        }
    }

    private Optional<TermOccurrence> resolveAnnotation(Element rdfaElem, Asset<?> source) {
        final String termId = fullIri(rdfaElem.attr(Constants.RDFa.RESOURCE));
        if (termId.isEmpty()) {
            LOG.trace("No term identifier found in RDFa element {}. Skipping it.", rdfaElem);
            return Optional.empty();
        }
        final URI termUri = URI.create(termId);
        verifyTermExists(rdfaElem, termUri, termId);
        final TermOccurrence occurrence = createOccurrence(termUri, source);
        occurrence.getTarget().setSelectors(selectorGenerators.generateSelectors(rdfaElem));
        occurrence.setUri(resolveOccurrenceId(rdfaElem, source));
        final String strScore = rdfaElem.attr(SCORE_ATTRIBUTE);
        if (!strScore.isEmpty()) {
            try {
                final Double score = Double.parseDouble(strScore);
                occurrence.setScore(score);
            } catch (NumberFormatException e) {
                occurrence.setScore(0.0);
                LOG.error("Unable to parse score.", e);
            }
        } else {
            // Occurrence already approved in text analysis output (probably from a previous processing of the content)
            occurrence.markApproved();
        }
        return Optional.of(occurrence);
    }

    private void verifyTermExists(Element rdfaElem, URI termUri, String termId) {
        if (existingTermIds.contains(termId)) {
            return;
        }
        if (!termService.exists(termUri)) {
            throw new AnnotationGenerationException("Term with id " + Utils.uriToString(termUri) + " denoted by RDFa element '" + rdfaElem + "' not found.");
        }
        existingTermIds.add(termId);
    }

    private URI resolveOccurrenceId(Element rdfaElem, Asset<?> source) {
        final String base = TermOccurrence.resolveContext(source.getUri()) + "/";
        String about = rdfaElem.attr("about");
        if (about.startsWith(BNODE_PREFIX)) {
            about = about.substring(BNODE_PREFIX.length());
        }
        return URI.create(base + about);
    }

    private boolean existsApproved(TermOccurrence newOccurrence) {
        final OccurrenceTarget target = newOccurrence.getTarget();
        assert target != null;
        final Set<Selector> selectors = target.getSelectors();
        for (TermOccurrence to : existingOccurrences) {
            if (!to.getTerm().equals(newOccurrence.getTerm())) {
                continue;
            }
            final OccurrenceTarget existingTarget = to.getTarget();
            assert existingTarget != null;
            assert existingTarget.getSource().equals(target.getSource());
            // Same term, contains at least one identical selector
            if (existingTarget.getSelectors().stream().anyMatch(selectors::contains) && !to.isSuggested()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean supports(Asset<?> source) {
        if (source instanceof Term) {
            return true;
        }
        if (!(source instanceof File sourceFile)) {
            return false;
        }
        if (sourceFile.getLabel().endsWith("html") || sourceFile.getLabel().endsWith("htm")) {
            return true;
        }
        final Optional<String> probedContentType = documentManager.getContentType(sourceFile);
        return probedContentType.isPresent() && (probedContentType.get()
                                                                  .equals(MediaType.TEXT_HTML_VALUE) || probedContentType.get()
                                                                                                                         .equals(MediaType.APPLICATION_XHTML_XML_VALUE));
    }
}
