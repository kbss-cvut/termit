package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Supports resolving asset repository context. */
@Component
public class AssetContextMapper {

    private static final List<URI> ASSET_TYPES = List.of(
            URI.create(SKOS.CONCEPT_SCHEME),
            URI.create(SKOS.CONCEPT),
            URI.create(Vocabulary.s_c_document),
            URI.create(Vocabulary.s_c_file));

    private final EntityManager em;

    public AssetContextMapper(EntityManager em) {
        this.em = em;
    }

    /**
     * Resolves the repository context where the asset with the specified identifier is declared.
     *
     * <p>Works for vocabularies, terms, documents, and files.
     *
     * @param assetIri Asset identifier
     * @return Repository context identifier
     * @throws cz.cvut.kbss.termit.exception.AmbiguousRepositoryContextException When unable to determine asset
     *     repository context
     */
    public URI getContext(@Nonnull URI assetIri) {
        Objects.requireNonNull(assetIri);
        return em.createNativeQuery(
                        "SELECT ?g WHERE {" + "VALUES ?type { "
                                + ASSET_TYPES.stream().map(Utils::uriToString).collect(Collectors.joining(" "))
                                + " } GRAPH ?g { ?asset a ?type . } }",
                        URI.class)
                .setParameter("asset", assetIri)
                .getSingleResult();
    }
}
