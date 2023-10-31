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
package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertThat(result, containsString(sut.timestamp.toString().replace("-", "")
                                                       .replace(":", "")));
    }
}
