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
package cz.cvut.kbss.termit.dto;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents input passed to the text analysis service.
 * <p>
 * Mainly contains the content to analyze and identification of the vocabularies whose terms will be used in the text
 * analysis.
 */
public class TextAnalysisInput {

    /**
     * Text content to analyze.
     */
    private String content;

    /**
     * Language of the text content.
     */
    private String language;

    /**
     * URI of the repository containing vocabularies whose terms are used in the text analysis.
     */
    private URI vocabularyRepository;

    /**
     * Username to access the repository
     */
    private String vocabularyRepositoryUserName;

    /**
     * Password to access the repository
     */
    private String vocabularyRepositoryPassword;

    /**
     * URIs of contexts containing vocabularies whose terms are used in the text analysis. Optional.
     * <p>
     * If not specified, the whole {@link #vocabularyRepository} is searched for terms.
     */
    private Set<URI> vocabularyContexts;

    public TextAnalysisInput() {
    }

    public TextAnalysisInput(String content, String language, URI vocabularyRepository) {
        this.content = content;
        this.language = language;
        this.vocabularyRepository = vocabularyRepository;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public URI getVocabularyRepository() {
        return vocabularyRepository;
    }

    public void setVocabularyRepository(URI vocabularyRepository) {
        this.vocabularyRepository = vocabularyRepository;
    }

    public String getVocabularyRepositoryUserName() {
        return vocabularyRepositoryUserName;
    }

    public void setVocabularyRepositoryUserName(String vocabularyRepositoryUserName) {
        this.vocabularyRepositoryUserName = vocabularyRepositoryUserName;
    }

    public String getVocabularyRepositoryPassword() {
        return vocabularyRepositoryPassword;
    }

    public void setVocabularyRepositoryPassword(String vocabularyRepositoryPassword) {
        this.vocabularyRepositoryPassword = vocabularyRepositoryPassword;
    }

    public Set<URI> getVocabularyContexts() {
        return vocabularyContexts;
    }

    public void setVocabularyContexts(Set<URI> vocabularyContexts) {
        this.vocabularyContexts = vocabularyContexts;
    }

    public void addVocabularyContext(URI vocabularyContext) {
        if (vocabularyContexts == null) {
            this.vocabularyContexts = new HashSet<>();
        }
        vocabularyContexts.add(vocabularyContext);
    }

    @Override
    public String toString() {
        assert content != null;
        return "TextAnalysisInput{" +
                "content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", vocabularyRepository=" + vocabularyRepository +
                ", vocabularyContexts=" + vocabularyContexts +
                ", language=" + language +
                '}';
    }
}
