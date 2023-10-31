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
package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.LanguageRetrievalException;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides the language of term states.
 * <p>
 * That is, this service provides access to the set of options for {@link cz.cvut.kbss.termit.model.Term} state.
 */
@Service
public class TermStateLanguageService {

    private final Resource termStatesLanguageTtl;

    private List<RdfsResource> cache;

    public TermStateLanguageService(@Qualifier("termStatesLanguage") Resource termStatesLanguageTtl) {
        this.termStatesLanguageTtl = termStatesLanguageTtl;
    }

    /**
     * Gets the possible values of term state.
     *
     * @return List of resources representing possible term states
     */
    public List<RdfsResource> getTermStates() {
        if (cache == null) {
            this.cache = loadTermStates();
        }
        return cache.stream().map(RdfsResource::new).collect(Collectors.toList());
    }

    @NotNull
    private List<RdfsResource> loadTermStates() {
        try {
            final ValueFactory vf = SimpleValueFactory.getInstance();
            final Model model = Rio.parse(termStatesLanguageTtl.getInputStream(), RDFFormat.TURTLE);
            return model.filter(null, RDF.TYPE, vf.createIRI(Vocabulary.s_c_stav_pojmu))
                        .stream().map(s -> {
                        final org.eclipse.rdf4j.model.Resource state = s.getSubject();
                        final RdfsResource res = new RdfsResource();
                        res.setUri(URI.create(state.stringValue()));
                        final Model statements = model.filter(state, null, null);
                        res.setTypes(statements.filter(state, RDF.TYPE, null).stream()
                                               .map(ts -> ts.getObject().stringValue()).collect(Collectors.toSet()));
                        res.setLabel(Utils.resolveTranslations(state, SKOS.PREF_LABEL, statements));
                        res.setComment(Utils.resolveTranslations(state, SKOS.SCOPE_NOTE, statements));
                        return res;
                    }).collect(Collectors.toList());
        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            throw new LanguageRetrievalException(
                    "Unable to load term states language from file " + termStatesLanguageTtl.getFilename(), e);
        }
    }

    /**
     * Gets the initial term state.
     * <p>
     * That is, gets the state that a new term should be assigned by default. Note that the state language may not
     * specify an initial state.
     *
     * @return Optional initial term state
     */
    public Optional<RdfsResource> getInitialState() {
        return getTermStates().stream().filter(s -> s.getTypes().contains(Vocabulary.s_c_uvodni_stav_pojmu))
                              .findFirst();
    }
}
