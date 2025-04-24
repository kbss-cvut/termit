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
package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetToRdfsResourceMapperTest {

    private final AssetToRdfsResourceMapper sut = new AssetToRdfsResourceMapper(Environment.LANGUAGE);

    @Test
    void visitTermTransformsTermToRdfsResource() {
        final Term t = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        t.accept(sut);
        assertEquals(sut.getRdfsResource().getUri(), t.getUri());
        assertEquals(sut.getRdfsResource().getLabel(), t.getLabel());
        assertEquals(sut.getRdfsResource().getComment(), t.getDefinition());
        assertThat(sut.getRdfsResource().getTypes(), hasItem(SKOS.CONCEPT));
    }

    @Test
    void visitTermAddsTermTypesToTargetRdfsResource() {
        final Term t = Generator.generateTermWithId();
        t.addType(Generator.generateUri().toString());
        t.accept(sut);
        assertThat(sut.getRdfsResource().getTypes(), hasItems(t.getTypes().toArray(new String[]{})));
    }

    @Test
    void visitVocabularyTransformsVocabularyToRdfsResource() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        v.accept(sut);
        assertEquals(sut.getRdfsResource().getUri(), v.getUri());
        assertEquals(sut.getRdfsResource().getLabel(), v.getLabel());
        assertThat(sut.getRdfsResource().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik));
    }

    @Test
    void visitVocabularyTransformsResourceToRdfsResource() {
        final Document d = Generator.generateDocumentWithId();
        d.accept(sut);
        assertEquals(sut.getRdfsResource().getUri(), d.getUri());
        assertEquals(sut.getRdfsResource().getLabel(), MultilingualString.create(d.getLabel(), Environment.LANGUAGE));
        assertThat(sut.getRdfsResource().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_dokument));
    }
}
