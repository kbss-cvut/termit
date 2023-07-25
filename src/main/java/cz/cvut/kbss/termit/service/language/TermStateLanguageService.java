package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
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
                        final Resource type = s.getSubject();
                        final RdfsResource res = new RdfsResource();
                        res.setUri(URI.create(type.stringValue()));
                        final Model statements = model.filter(type, null, null);
                        res.setTypes(statements.filter(type, RDF.TYPE, null).stream()
                                               .map(ts -> ts.getObject().stringValue()).collect(Collectors.toSet()));
                        res.setLabel(extractString(type, RDFS.LABEL, statements));
                        res.setComment(extractString(type, RDFS.COMMENT, statements));
                        return res;
                    }).collect(Collectors.toList());
        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            throw new TermItException("Unable to load term states language file.", e);
        }
    }

    private MultilingualString extractString(Resource subj, IRI property, Model model) {
        final MultilingualString s = new MultilingualString();
        model.filter(subj, property, null).forEach(st -> {
            if (st.getObject().isLiteral()) {
                ((Literal) st.getObject()).getLanguage()
                                          .ifPresent(lang -> s.set(lang, ((Literal) st.getObject()).getLabel()));
            }
        });
        return s;
    }
}
