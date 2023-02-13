package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.termit.dto.listing.DocumentDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DtoMapper {

    DocumentDto documentToDocumentDto(Document document);

    VocabularyDto vocabularyToVocabularyDto(Vocabulary vocabulary);
}
