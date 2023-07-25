package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.LanguageRetrievalException;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides the language of term states.
 * <p>
 * That is, this service provides access to the set of options for {@link cz.cvut.kbss.termit.model.Term} state.
 */
@Service
public class TermStateLanguageService {

    private final ClassPathResource termStatesLanguageTtl;

    public TermStateLanguageService(@Qualifier("termStatesLanguage") ClassPathResource termStatesLanguageTtl) {
        this.termStatesLanguageTtl = termStatesLanguageTtl;
    }

    /**
     * Gets the possible values of term state.
     *
     * @return List of resources representing possible term states
     */
    public List<RdfsResource> getTermStates() {
        try {
            final ValueFactory vf = SimpleValueFactory.getInstance();
            final Model model = Rio.parse(termStatesLanguageTtl.getInputStream(), RDFFormat.TURTLE);
            return model.filter(null, RDF.TYPE, vf.createIRI(Vocabulary.s_c_stav_pojmu))
                        .stream().map(s -> {
                        final Resource state = s.getSubject();
                        final RdfsResource res = new RdfsResource();
                        res.setUri(URI.create(state.stringValue()));
                        final Model statements = model.filter(state, null, null);
                        res.setTypes(statements.filter(state, RDF.TYPE, null).stream()
                                               .map(ts -> ts.getObject().stringValue()).collect(Collectors.toSet()));
                        res.setLabel(Utils.resolveTranslations(state, RDFS.LABEL, statements));
                        res.setComment(Utils.resolveTranslations(state, RDFS.COMMENT, statements));
                        return res;
                    }).collect(Collectors.toList());
        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            throw new LanguageRetrievalException("Unable to load term states language from file " + termStatesLanguageTtl.getPath(), e);
        }
    }
}
