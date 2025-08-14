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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.dto.statistics.CountableAssetType;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.dto.statistics.TermTypeDistributionDto;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calculates statistics based on data in the repository.
 */
@Repository
public class StatisticsDao {

    private static final RdfsResource NO_TYPE = initNoType();

    private final EntityManager em;

    private final Configuration config;

    public StatisticsDao(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config;
    }

    private static RdfsResource initNoType() {
        final RdfsResource noType = new RdfsResource(URI.create(Vocabulary.ONTOLOGY_IRI_TERMIT + "/bez-typu"),
                                                     new LangString("Nevyplněno", "cs"), null,
                                                     SKOS.CONCEPT);
        noType.getLabel().set("en", "No type");
        return noType;
    }

    /**
     * Gets term distribution in vocabularies.
     * <p>
     * Snapshots are excluded from the count.
     *
     * @return List of term counts per vocabulary
     */
    public List<DistributionDto> getTermDistribution() {
        final String query = Utils.loadQuery("statistics/termDistribution.rq");
        final List<DistributionDto> result = (List<DistributionDto>) em.createNativeQuery(query).getResultStream()
                                                                       .map(row -> {
                                                                           final Object[] bindings = (Object[]) row;
                                                                           assert bindings.length == 4;
                                                                           final URI vocabulary = (URI) bindings[0];
                                                                           final String vocabularyLanguage = (String) bindings[3];
                                                                           final LangString label = sanitizeLabel(
                                                                                   bindings[1], vocabularyLanguage);
                                                                           final BigInteger count = (BigInteger) bindings[2];
                                                                           return new DistributionDto(
                                                                                   new RdfsResource(vocabulary, label,
                                                                                                    null,
                                                                                                    Vocabulary.s_c_slovnik),
                                                                                   count.intValue());
                                                                       }).collect(Collectors.toList());
        consolidateTranslations(result);
        return result;
    }

    private static void consolidateTranslations(List<DistributionDto> result) {
        DistributionDto previous = null;
        final Iterator<DistributionDto> it = result.iterator();
        while (it.hasNext()) {
            final DistributionDto current = it.next();
            if (previous != null && previous.getResource().getUri().equals(current.getResource().getUri())) {
                previous.getResource().getLabel().getValue().putAll(current.getResource().getLabel().getValue());
                it.remove();
            } else {
                previous = current;
            }
        }
    }

    private LangString sanitizeLabel(Object label, String primaryLanguage) {
        if (label instanceof LangString) {
            return (LangString) label;
        }
        return new LangString(label.toString(), primaryLanguage);
    }

    /**
     * Gets the number of items of the specified type.
     * <p>
     * Snapshots are excluded from the count.
     *
     * @param type Type of asset to count
     * @return Number of items
     */
    public int getAssetCount(@Nonnull CountableAssetType type) {
        Objects.requireNonNull(type);
        if (CountableAssetType.TERM == type) {
            return em.createNativeQuery(Utils.loadQuery("statistics/termCount.rq"), Integer.class).getSingleResult();
        } else {
            return em.createNativeQuery(Utils.loadQuery("statistics/assetCount.rq"), Integer.class)
                     .setParameter("assetType", URI.create(type.getTypeUri())).getSingleResult();
        }
    }

    /**
     * Resolves distribution of types of terms in vocabularies.
     *
     * @param types Types to include in statistics, should not be empty
     * @return List of distribution objects for each vocabulary
     */
    public List<TermTypeDistributionDto> getTermTypeDistribution(@Nonnull List<RdfsResource> types) {
        Objects.requireNonNull(types);
        final String query = Utils.loadQuery("statistics/termTypeDistribution.rq");
        final Map<URI, RdfsResource> typeMap = types.stream().collect(Collectors.toMap(RdfsResource::getUri, r -> r));
        typeMap.put(NO_TYPE.getUri(), NO_TYPE);
        final List<TermTypeDistributionDto> result = (List<TermTypeDistributionDto>) em.createNativeQuery(query)
                                                                                       .setParameter("types", types)
                                                                                       .getResultStream().map(row -> {
                    final Object[] bindings = (Object[]) row;
                    assert bindings.length == 5;
                    final URI vocabulary = (URI) bindings[0];
                    final String vocabularyLanguage = (String) bindings[4];
                    final LangString label = sanitizeLabel(
                            bindings[1], vocabularyLanguage);
                    final URI type = (URI) bindings[2];
                    final BigInteger count = (BigInteger) bindings[3];
                    final TermTypeDistributionDto res = new TermTypeDistributionDto();
                    res.setVocabulary(new RdfsResource(vocabulary, label,
                                                       null,
                                                       Vocabulary.s_c_slovnik));
                    res.getTypeDistribution().add(new DistributionDto(
                            typeMap.get(type), count.intValue()));
                    return res;
                }).collect(Collectors.toList());
        consolidateTranslationsAndTypes(result);
        return result;
    }

    private static void consolidateTranslationsAndTypes(List<TermTypeDistributionDto> result) {
        TermTypeDistributionDto previous = null;
        final Iterator<TermTypeDistributionDto> it = result.iterator();
        while (it.hasNext()) {
            final TermTypeDistributionDto current = it.next();
            if (previous != null && previous.getVocabulary().getUri().equals(current.getVocabulary().getUri())) {
                previous.getVocabulary().getLabel().getValue().putAll(current.getVocabulary().getLabel().getValue());
                if (previous.getTypeDistribution().stream().noneMatch(ddto -> ddto.getResource().getUri().equals(
                        current.getTypeDistribution().get(0).getResource().getUri()))) {
                    previous.getTypeDistribution().add(current.getTypeDistribution().get(0));
                }
                it.remove();
            } else {
                previous = current;
            }
        }
    }
}
