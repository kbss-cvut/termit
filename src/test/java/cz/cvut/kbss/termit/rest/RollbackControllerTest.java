package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord_;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.changetracking.ChangeRollbackService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.stream.Stream;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class RollbackControllerTest extends BaseControllerTestRunner {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    @Mock
    private IdentifierResolver idResolverMock;

    @Mock
    private ChangeRollbackService changeRollbackService;

    @InjectMocks
    private RollbackController sut;

    @BeforeEach
    void setUp() {
        this.setUp(sut);
    }

    private static UpdateChangeRecord createUpdateRecord(Asset<?> asset) {
        final UpdateChangeRecord changeRecord = new UpdateChangeRecord();
        final String recordLocalName = "update" + Generator.randomInt();
        changeRecord.setUri(URI.create(UpdateChangeRecord_.entityClassIRI + "/" + recordLocalName));
        changeRecord.setChangedEntity(asset.getUri());
        return changeRecord;
    }

    private static Stream<Arguments> argumentsStream() {
        final String suffix = "/{localName}/history/{changeRecord}/rollback";

        return Stream.of(
                Arguments.of("/vocabularies" + suffix, Generator.generateVocabularyWithId()),
                Arguments.of("/terms" + suffix, Generator.generateTermWithId())
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsStream")
    void rollbackChangeResolvesUpdateChangeRecordAndRollsbackTheChange(String endpointPath, Asset<?> asset) throws Exception {
        final String localName = IdentifierResolver.extractIdentifierFragment(asset.getUri());
        final String namespace = IdentifierResolver.extractIdentifierNamespace(asset.getUri());
        final UpdateChangeRecord record = createUpdateRecord(asset);
        final String recordLocalName = IdentifierResolver.extractIdentifierFragment(record.getUri());

        when(idResolverMock.resolveIdentifier(namespace, localName)).thenReturn(asset.getUri());
        when(changeRollbackService.findRecordByLocalName(recordLocalName)).thenReturn(record);

        mockMvc.perform(post(endpointPath, localName, recordLocalName).param(Constants.QueryParams.NAMESPACE, namespace))
                .andExpect(status().isNoContent());

        verify(changeRollbackService).findRecordByLocalName(recordLocalName);
        verify(changeRollbackService).rollback(record);
    }

    @ParameterizedTest
    @MethodSource("argumentsStream")
    void rollbackChangeReturnsUnprocessableContentWhenResolvedChangeRecordDoesNotMatchChangedAsset(String endpointPath, Asset<?> asset) throws Exception {
        final String localName = IdentifierResolver.extractIdentifierFragment(asset.getUri());
        final String namespace = IdentifierResolver.extractIdentifierNamespace(asset.getUri());
        final UpdateChangeRecord record = createUpdateRecord(asset);
        record.setChangedEntity(Generator.generateUri());
        final String recordLocalName = IdentifierResolver.extractIdentifierFragment(record.getUri());

        when(idResolverMock.resolveIdentifier(namespace, localName)).thenReturn(asset.getUri());
        when(changeRollbackService.findRecordByLocalName(recordLocalName)).thenReturn(record);

        mockMvc.perform(post(endpointPath, localName, recordLocalName).param(Constants.QueryParams.NAMESPACE, namespace))
               .andExpect(status().isUnprocessableContent());

        verify(changeRollbackService).findRecordByLocalName(recordLocalName);
        verify(changeRollbackService, never()).rollback(record);
    }
}
