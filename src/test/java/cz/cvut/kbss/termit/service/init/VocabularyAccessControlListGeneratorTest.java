package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyAccessControlListGeneratorTest {

    @Mock
    private EntityManager em;

    @Mock
    private VocabularyRepositoryService vocabularyService;

    @Mock
    private AccessControlListService aclService;

    @InjectMocks
    private VocabularyAccessControlListGenerator sut;

    @Test
    void generateMissingAccessControlListsGeneratesAccessControlListsForVocabulariesWithoutACLs() {
        final List<Vocabulary> vocabs = List.of(Generator.generateVocabularyWithId(), Generator.generateVocabularyWithId());
        final List<AccessControlList> acls = List.of(Generator.generateAccessControlList(false), Generator.generateAccessControlList(false));
        assertEquals(vocabs.size(), acls.size());
        final TypedQuery<URI> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(em.createNativeQuery(anyString(), eq(URI.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(vocabs.stream().map(Asset::getUri).collect(Collectors.toList()));
        for (int i = 0; i < vocabs.size(); i++) {
            when(vocabularyService.findRequired(vocabs.get(i).getUri())).thenReturn(vocabs.get(i));
            when(aclService.createFor(vocabs.get(i))).thenReturn(acls.get(i));
        }

        sut.generateMissingAccessControlLists();
        for (int i = 0; i < vocabs.size(); i++) {
            verify(aclService).createFor(vocabs.get(i));
            assertEquals(acls.get(i).getUri(), vocabs.get(i).getAcl());
        }
    }
}
