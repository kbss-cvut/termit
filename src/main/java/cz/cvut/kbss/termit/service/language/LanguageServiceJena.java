/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.dto.TermInfo;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A service that fetches parts of the UFO-compliant language for the use in TermIt.
 * <p>
 * In this class: - lang = natural language tag, e.g. "cs", or "en" - language = UFO language, e.g. OntoUML, or Basic
 * Language
 */
@Qualifier("jena")
@Service
public class LanguageServiceJena extends LanguageService {

    private static final Logger LOG = LoggerFactory.getLogger(LanguageServiceJena.class);

    @Autowired
    public LanguageServiceJena(ClassPathResource languageTtlUrl) {
        super(languageTtlUrl);
    }

    /**
     * Gets all types with labels in the given language.
     *
     * @param lang label language
     * @return List of types as {@code Term}s
     */
    public List<Term> getTypesForLang(String lang) {
        try {
            final Model m = ModelFactory.createOntologyModel();
            m.read(resource.getURL().toString(), "text/turtle");

            final List<Term> terms = new ArrayList<>();
            m.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource(
                cz.cvut.kbss.jopa.vocabulary.SKOS.CONCEPT))
             .forEachRemaining(c -> {
                 final Term t = new Term();
                 t.setUri(URI.create(c.getURI()));
                 if (c.getProperty(SKOS.prefLabel, lang) != null) {
                     t.setLabel(MultilingualString.create(c.getProperty(SKOS.prefLabel, lang).getObject().asLiteral().getString(), lang));
                 } else {
                     t.setLabel(MultilingualString.create(t.getUri().toString(), null));
                 }

                 final Statement st = c.getProperty(SKOS.definition, lang);
                 if (st != null) {
                     t.setDescription(st.getObject().asLiteral().getString());
                 }
                 t.setSubTerms(c.listProperties(SKOS.narrower)
                                .mapWith(s -> new TermInfo(URI.create(s.getObject().asResource().getURI()))).toSet());
                 terms.add(t);
             });
            return terms;
        } catch (Exception e) {
            LOG.error("Unable to retrieve types for language {}.", lang, e);
            return Collections.emptyList();
        }
    }
}
