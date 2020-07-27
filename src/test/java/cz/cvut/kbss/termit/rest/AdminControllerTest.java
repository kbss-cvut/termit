package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.service.jmx.AppAdminBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest extends BaseControllerTestRunner {

    @Mock
    private AppAdminBean adminBean;

    @InjectMocks
    private AdminController sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        setUp(sut);
    }

    @Test
    void invalidateCachesInvokesCacheInvalidationOnService() throws Exception {
        mockMvc.perform(delete("/admin/cache")).andExpect(status().isNoContent());
        verify(adminBean).invalidateCaches();
    }
}
