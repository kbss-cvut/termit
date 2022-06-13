package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

class SnapshotCreatorTest {

    private static final String SNAPSHOT_SEPARATOR = "/snapshot";

    private SnapshotCreator sut;

    @BeforeEach
    void setUp() {
        final Configuration.Namespace.NamespaceDetail config = new Configuration.Namespace.NamespaceDetail();
        config.setSeparator(SNAPSHOT_SEPARATOR);
        final Configuration.Namespace ns = new Configuration.Namespace();
        ns.setSnapshot(config);
        final Configuration configuration = new Configuration();
        configuration.setNamespace(ns);

        this.sut = new SnapshotCreator(configuration) {

            @Override
            public Snapshot createSnapshot(Vocabulary asset) {
                return null;
            }
        };
    }

    @Test
    void getSnapshotSuffixUsesConfiguredSeparatorAndCurrentTimestampToGenerateSnapshotIdentifier() {
        final String result = sut.getSnapshotSuffix();
        assertThat(result, startsWith(SNAPSHOT_SEPARATOR + "/"));
        assertThat(result, containsString(LocalDate.now().toString()));
    }
}
