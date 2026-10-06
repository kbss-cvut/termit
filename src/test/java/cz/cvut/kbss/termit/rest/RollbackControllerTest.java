package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord_;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.changetracking.ChangeRollbackService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class RollbackControllerTest extends BaseControllerTestRunner {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    @Mock
    private ChangeRollbackService changeRollbackService;

    @BeforeEach
    void setUp() {
        RollbackController sut = new RollbackController(new IdentifierResolver(config), config, changeRollbackService);
        this.setUp(sut);
    }

    private static UpdateChangeRecord createUpdateRecord(Asset<?> asset) {
        final UpdateChangeRecord changeRecord = new UpdateChangeRecord();
        final String recordLocalName = "update" + Generator.randomInt();
        changeRecord.setUri(URI.create(UpdateChangeRecord_.entityClassIRI + "/" + recordLocalName));
        changeRecord.setChangedEntity(asset.getUri());
        return changeRecord;
    }

    @Test
    void rollbackResolvesUpdateChangeRecordAndRollsbackTheChange() throws Exception {
        final Vocabulary asset = Generator.generateVocabularyWithId();
        final UpdateChangeRecord record = createUpdateRecord(asset);
        final String recordLocalName = IdentifierResolver.extractIdentifierFragment(record.getUri());
        final String recordNamespace = IdentifierResolver.extractIdentifierNamespace(record.getUri());

        when(changeRollbackService.findUpdateRecord(record.getUri())).thenReturn(record);

        mockMvc.perform(post("/history/{localName}/rollback", recordLocalName)
                       .param(Constants.QueryParams.NAMESPACE, recordNamespace)
               ).andExpect(status().isNoContent());

        verify(changeRollbackService).findUpdateRecord(record.getUri());
        verify(changeRollbackService).rollback(record);
    }

    @Test
    void rollbackResolvesPreVersion5ChangeRecordUri() throws Exception {
        final Term asset = Generator.generateTermWithId();
        final UpdateChangeRecord record = createUpdateRecord(asset);

        final String recordLocalName = "instance-1085384276";
        final String recordNamespace = "http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/úprava-entity/";
        record.setUri(URI.create(recordNamespace + recordLocalName));

        when(changeRollbackService.findUpdateRecord(record.getUri())).thenReturn(record);

        mockMvc.perform(post("/history/{localName}/rollback", recordLocalName)
                .param(Constants.QueryParams.NAMESPACE, recordNamespace)
        ).andExpect(status().isNoContent());

        verify(changeRollbackService).findUpdateRecord(record.getUri());
        verify(changeRollbackService).rollback(record);
    }
}
