/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.exception.LanguageRetrievalException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides UFO-compliant language for classification of terms.
 * <p>
 * It uses RDF4J to load and term types from the provided configuration file.
 */
@Service
public class UfoTermTypesService {

    private final Resource languageTtlUrl;

    private List<Term> cache;

    @Autowired
    public UfoTermTypesService(@Qualifier("termTypesLanguage") Resource languageTtlUrl) {
        this.languageTtlUrl = languageTtlUrl;
    }

    /**
     * Gets all types.
     *
     * @return List of types as {@code Term}s
     */
    public List<Term> getTypes() {
        if (cache == null) {
            cache = loadTermTypes();
        }
        return cache.stream().map(t -> {
            final Term copy = new Term(t.getUri());
            copy.setLabel(new MultilingualString(t.getLabel().getValue()));
            copy.setDescription(new MultilingualString(t.getDescription().getValue()));
            copy.setSubTerms(t.getSubTerms().stream().map(ti -> new TermInfo(ti.getUri())).collect(Collectors.toSet()));
            return copy;
        }).collect(Collectors.toList());
    }

    @Nonnull
    private List<Term> loadTermTypes() {
        try {
            final Model model = Rio.parse(languageTtlUrl.getInputStream(), RDFFormat.TURTLE);
            return model.filter(null, RDF.TYPE, SKOS.CONCEPT)
                        .stream().map(s -> {
                        final org.eclipse.rdf4j.model.Resource type = s.getSubject();
                        final Term term = new Term(URI.create(type.stringValue()));
                        final Model statements = model.filter(type, null, null);
                        term.setLabel(Utils.resolveTranslations(type, SKOS.PREF_LABEL, statements));
                        term.setDescription(Utils.resolveTranslations(type, SKOS.SCOPE_NOTE, statements));
                        final Set<URI> subTerms = statements.filter(type, SKOS.NARROWER, null).stream()
                                                   .filter(st -> st.getObject().isIRI())
                                                   .map(st -> URI.create(st.getObject().stringValue()))
                                                   .collect(Collectors.toSet());
                        model.filter(null, SKOS.BROADER, type).stream()
                                  .filter(st -> st.getSubject().isIRI())
                                  .map(st -> URI.create(st.getSubject().stringValue()))
                                  .forEach(subTerms::add);
                        term.setSubTerms(subTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
                        return term;
                    }).collect(Collectors.toList());
        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            throw new LanguageRetrievalException("Unable to load term types from file " + languageTtlUrl.getFilename(), e);
        }
    }
}
