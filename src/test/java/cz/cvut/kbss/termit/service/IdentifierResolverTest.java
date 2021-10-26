/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentifierResolverTest {

    private IdentifierResolver sut;

    @BeforeEach
    void setUp() {
        this.sut = new IdentifierResolver();
    }

    @Test
    void normalizeTransformsValueToLowerCase() {
        final String value = "CapitalizedSTring";
        assertEquals(value.toLowerCase(), IdentifierResolver.normalize(value));
    }

    @Test
    void normalizeTrimsValue() {
        final String value = "   DDD   ";
        assertEquals(value.trim().toLowerCase(), IdentifierResolver.normalize(value));
    }

    @Test
    void normalizeReplacesSpacesWithDashes() {
        final String value = "Catherine Halsey";
        assertEquals("catherine-halsey", IdentifierResolver.normalize(value));
    }

    @Test
    void normalizeToAsciiChangesCzechAccutesToAsciiCharacters() {
        final String value = "Strukturální Plán";
        assertEquals("strukturalni-plan", IdentifierResolver.normalizeToAscii(value));
    }

    @Test
    void normalizeToAsciiChangesCzechAdornmentsToAsciiCharacters() {
        final String value = "předzahrádka";
        assertEquals("predzahradka", IdentifierResolver.normalizeToAscii(value));
    }

    @Test
    void normalizeReplacesForwardSlashesWithDashes() {
        final String value = "Slovník vyhlášky č. 500/2006 Sb.";
        assertEquals("slovník-vyhlášky-č.-500-2006-sb.", IdentifierResolver.normalize(value));
    }

    @Test
    void normalizeReplacesBackwardSlashesWithDashes() {
        final String value = "C:\\Users";
        assertEquals("c:-users", IdentifierResolver.normalize(value));
    }

    @Test
    void normalizeRemovesParentheses() {
        final String value = "Dokument pro Slovník zákona č. 183/2006 Sb. (Stavební zákon)";
        assertEquals("dokument-pro-slovník-zákona-č.-183-2006-sb.-stavební-zákon", IdentifierResolver.normalize(value));
    }

    @Test
    void normalizeRemovesQueryParameterDelimiters() {
        final String value = "legal-content/SK/TXT/HTML/?uri=CELEX:32010R0996&form=sk";
        assertEquals("legal-content-sk-txt-html-uri=celex:32010r0996form=sk", IdentifierResolver.normalize(value));
    }

    @Test
    void generateIdentifierAppendsNormalizedComponentsToSpecifiedNamespace() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/";
        final String compOne = "Catherine";
        final String compTwo = "Halsey";
        final String result = sut.generateIdentifier(namespace, compOne, compTwo).toString();
        assertEquals(namespace + "catherine-halsey", result);
    }

    @Test
    void generateIdentifierAppendsSlashWhenNamespaceDoesNotEndWithIt() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit";
        final String compOne = "Catherine";
        final String compTwo = "Halsey";
        final String result = sut.generateIdentifier(namespace, compOne, compTwo).toString();
        assertEquals(namespace + "/catherine-halsey", result);
    }

    @Test
    void generateIdentifierDoesNotAppendSlashWhenNamespaceEndsWithHashTag() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit#";
        final String compOne = "Catherine";
        final String compTwo = "Halsey";
        final String result = sut.generateIdentifier(namespace, compOne, compTwo).toString();
        assertEquals(namespace + "catherine-halsey", result);
    }

    @Test
    void generateIdentifierThrowsIllegalArgumentWhenNoComponentsAreProvided() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sut.generateIdentifier(Environment.BASE_URI));
        assertEquals("Must provide at least one component for identifier generation.", ex.getMessage());
    }

    @Test
    void generateIdentifierAppendsNormalizedComponentsToNamespaceLoadedFromConfig() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/";
        final String comp = "Metropolitan Plan";
        final String result = sut.generateIdentifier(namespace, comp).toString();
        assertEquals(namespace + "metropolitan-plan", result);
    }

    @Test
    void resolveIdentifierAppendsFragmentToSpecifiedNamespace() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/";
        final String fragment = "metropolitan-plan";
        assertEquals(namespace + fragment, sut.resolveIdentifier(namespace, fragment).toString());
    }

    @Test
    void resolveIdentifierAppendsSlashAndFragmentIfNamespaceDoesNotEndWithOne() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary";
        final String fragment = "metropolitan-plan";
        assertEquals(namespace + "/" + fragment, sut.resolveIdentifier(namespace, fragment).toString());
    }

    @Test
    void resolveIdentifierDoesNotAppendSlashIfNamespaceEndsWithHashTag() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary#";
        final String fragment = "metropolitan-plan";
        assertEquals(namespace + fragment, sut.resolveIdentifier(namespace, fragment).toString());
    }

    @Test
    void resolveIdentifierAppendsFragmentToNamespaceLoadedFromConfiguration() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/";
        final String fragment = "metropolitan-plan";
        assertEquals(namespace + fragment,
                sut.resolveIdentifier(namespace, fragment).toString());
    }

    @Test
    void extractIdentifierFragmentExtractsLastPartOfUri() {
        final URI uri = Generator.generateUri();
        final String result = IdentifierResolver.extractIdentifierFragment(uri);
        assertEquals(uri.toString().substring(uri.toString().lastIndexOf('/') + 1), result);
    }

    @Test
    void extractIdentifierFragmentExtractsFragmentFromUriWithUrlFragment() {
        final URI uri = URI.create("http://onto.fel.cvut.cz/ontologies/termit/vocabulary#test");
        assertEquals("test", IdentifierResolver.extractIdentifierFragment(uri));
    }

    @Test
    void extractIdentifierNamespaceExtractsNamespaceFromSlashBasedUri() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/";
        final String fragment = "metropolitan-plan";
        final String result = IdentifierResolver.extractIdentifierNamespace(URI.create(namespace + fragment));
        assertEquals(namespace, result);
    }

    @Test
    void extractIdentifierNamespaceExtractsNamespaceFromHashBasedUri() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary#";
        final String fragment = "metropolitan-plan";
        final String result = IdentifierResolver.extractIdentifierNamespace(URI.create(namespace + fragment));
        assertEquals(namespace, result);
    }

    @Test
    void resolveIdentifierWithNamespaceConstruction() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/metropolitan-plan";
        final String fragment = "locality";
        final URI result = sut
                .resolveIdentifier(sut.buildNamespace(namespace, "/pojem"), fragment);
        assertEquals(namespace + "/pojem/" + fragment, result.toString());
    }

    @Test
    void buildNamespaceAddsComponentsToBaseUri() {
        final String base = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary";
        final String cOne = "metropolitan-plan";
        final String cTwo = "/pojem";
        assertEquals(base + "/" + cOne + cTwo + "/", sut.buildNamespace(base, cOne, cTwo));
    }

    @Test
    void buildNamespaceReturnsNamespaceEndingWithSlash() {
        final String base = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/metropolitan-plan";
        assertThat(sut.buildNamespace(base, "/term"), endsWith("/"));
    }

    @Test
    void buildNamespaceReturnsBaseUriWithSlashWhenNoComponentsAreSpecified() {
        final String base = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/metropolitan-plan";
        assertEquals(base + "/", sut.buildNamespace(base));
    }

    @Test
    void buildNamespaceLoadsBaseUriFromConfiguration() {
        final String base = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary";
        final String component = "/term/";
        assertEquals(base + component, sut.buildNamespace(base, component));
    }

    @Test
    void sanitizeFileNameMakesSpecifiedLabelCompatibleWithLinuxFileNameRules() {
        final String label = "Zákon 130/2002.html";
        assertEquals("Zákon 130-2002.html", IdentifierResolver.sanitizeFileName(label));
    }

    @Test
    void sanitizeFileNameMakesSpecifiedLabelCompatibleWithWindowsFileNameRules() {
        final String label = "label:2002/130.html";
        assertEquals("label-2002-130.html", IdentifierResolver.sanitizeFileName(label));
    }

    @Test
    void sanitizeFileNameTrimsLeadingAndTrailingWhiteSpaces() {
        final String label = "  label enclosed in spaces   ";
        assertEquals(label.trim(), IdentifierResolver.sanitizeFileName(label));
    }

    @Test
    void generateIdentifierReturnsSpecifiedValueWhenItIsAlreadyUri() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabulary/";
        final String label = "http://onto.fel.cvut.cz/ontologies/termit/vocabularyOne";
        assertEquals(URI.create(label), sut.generateIdentifier(namespace, label));
    }

    @Test
    void generateIdentifierReturnsSpecifiedValueWithoutQueryParametersWhenItIsUri() {
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/resource/";
        final String label = "http://onto.fel.cvut.cz/ontologies/termit/resourceOne?test=one&test=two";
        assertEquals(URI.create("http://onto.fel.cvut.cz/ontologies/termit/resourceOne"),
                sut.generateIdentifier(namespace, label));
    }

    @Test
    void normalizePreservesCzechAccentsAndAdornmentsInString() {
        final String value = "Slovník vyhlášky číslo 117";
        assertEquals("slovník-vyhlášky-číslo-117", IdentifierResolver.normalize(value));
    }

    @Test
    void generateIdentifierPreservesCzechAccentsAndAdornments() {
        final String label = "Otevřená krajina";
        final String namespace = "https://onto.fel.cvut.cz/ontologies/page/slovn%c3%adk/datov%c3%bd/mpp-3.5-np/";
        assertEquals(URI.create(namespace + "otevřená-krajina"), sut.generateIdentifier(namespace, label));
    }
}
