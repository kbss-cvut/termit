package cz.cvut.kbss.termit.service.snapshot;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

class SnapshotCreatorTest {

    private static final String SNAPSHOT_SEPARATOR = "/snapshot";

    private final Instant timestamp = Instant.now();

    private SnapshotCreator<Vocabulary> sut;

    @BeforeEach
    void setUp() {
        final Configuration.Namespace.NamespaceDetail config = new Configuration.Namespace.NamespaceDetail();
        config.setSeparator(SNAPSHOT_SEPARATOR);
        final Configuration.Namespace ns = new Configuration.Namespace();
        ns.setSnapshot(config);
        final Configuration configuration = new Configuration();
        configuration.setNamespace(ns);

        this.sut = new SnapshotCreator<>(new IdentifierResolver(), configuration) {

            @Override
            public Vocabulary createSnapshot(Vocabulary asset) {
                return null;
            }
        };
        sut.setTimestamp(timestamp);
    }

    @Test
    void generateSnapshotIdentifierUsesConfiguredSeparatorAndCurrentTimestampToGenerateSnapshotIdentifier() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        final URI result = sut.generateSnapshotIdentifier(vocabulary);
        assertThat(result.toString(), startsWith(vocabulary.getUri() + SNAPSHOT_SEPARATOR + "/"));
        assertThat(result.toString(),
                   containsString(LocalDate.ofInstant(timestamp, ZoneId.systemDefault()).toString()));
    }
}
