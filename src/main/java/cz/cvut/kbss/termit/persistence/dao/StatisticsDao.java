package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.dto.statistics.CountableAssetType;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class StatisticsDao {

    private final EntityManager em;

    private final Configuration config;

    public StatisticsDao(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config;
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
                                                                           final URI vocabulary = (URI) bindings[0];
                                                                           final LangString label = sanitizeLabel(
                                                                                   bindings[1]);
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

    private LangString sanitizeLabel(Object label) {
        if (label instanceof LangString) {
            return (LangString) label;
        }
        return new LangString(label.toString(), config.getPersistence().getLanguage());
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
        if (CountableAssetType.TERM == type) {
            return em.createNativeQuery(Utils.loadQuery("statistics/termCount.rq"), Integer.class).getSingleResult();
        } else {
            return em.createNativeQuery(Utils.loadQuery("statistics/assetCount.rq"), Integer.class)
                     .setParameter("assetType", URI.create(type.getTypeUri())).getSingleResult();
        }
    }
}
