package cz.cvut.kbss.termit.dto.statistics;

import cz.cvut.kbss.termit.dto.RdfsResource;

public class TermTypeFrequencyDto {

    private RdfsResource vocabulary;

    /**
     * Type to number of terms
     */
    private DistributionDto termTypeFrequency;
}
