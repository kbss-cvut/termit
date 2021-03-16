package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.security.Principal;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/comments/";
    private static final String NAMESPACE = Vocabulary.ONTOLOGY_IRI_glosar + "/comments/";
    private static final String NAME = "Comment_" + Generator.randomInt();

    @Mock
    private IdentifierResolver idResolver;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
        when(idResolver.resolveIdentifier(NAMESPACE, NAME)).thenReturn(URI.create(NAMESPACE + NAME));
    }

    @Test
    void getByIdRetrievesCommentWithSpecifiedIdentifier() throws Exception {
        final Comment comment = generateComment();
        when(commentService.findRequired(comment.getUri())).thenReturn(comment);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + NAME)
                        .queryParam(Constants.QueryParams.NAMESPACE, NAMESPACE)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        final Comment result = readValue(mvcResult, Comment.class);
        assertNotNull(result);
        assertEquals(comment, result);
    }

    private static Comment generateComment() {
        final Comment comment = new Comment();
        comment.setContent("aaaa");
        comment.setAsset(Generator.generateUri());
        comment.setCreated(new Date());
        comment.setUri(URI.create(NAMESPACE + NAME));
        return comment;
    }

    @Test
    void updateUpdatesSpecifiedComment() throws Exception {
        final Comment comment = generateComment();
        mockMvc.perform(
                put(PATH + NAME).queryParam(Constants.QueryParams.NAMESPACE, NAMESPACE)
                        .content(toJson(comment))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(commentService).update(comment);
    }

    @Test
    void updateThrowsValidationExceptionWhenCommentUriDoesNotMatchRequestPath() throws Exception {
        final Comment comment = generateComment();
        comment.setUri(Generator.generateUri());
        final MvcResult mvcResult = mockMvc.perform(
                put(PATH + NAME).queryParam(Constants.QueryParams.NAMESPACE, NAMESPACE)
                        .content(toJson(comment))
                        .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isConflict()).andReturn();
        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertThat(errorInfo.getMessage(), containsString("does not match the ID of the specified entity"));
        verify(commentService, never()).update(any());
    }

    @Test
    void removeRemovesCommentWithSpecifiedIdentifier() throws Exception {
        final Comment comment = generateComment();
        when(commentService.findRequired(comment.getUri())).thenReturn(comment);
        mockMvc.perform(
                delete(PATH + NAME).queryParam(Constants.QueryParams.NAMESPACE, NAMESPACE))
                .andExpect(status().isNoContent());
        verify(commentService).remove(comment);
    }

    @Test
    void addReactionWithLikeTypeCreatesLikeForSpecifiedComment() throws Exception {
        final Comment comment = generateComment();
        when(commentService.findRequired(comment.getUri())).thenReturn(comment);
        final Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("testuser");
        final String likeType = "https://www.w3.org/ns/activitystreams#Like";
        mockMvc.perform(
                post(PATH + NAME + "/reactions").queryParam(Constants.QueryParams.NAMESPACE, NAMESPACE)
                        .queryParam("type", likeType).principal(principal))
                .andExpect(status().isNoContent());
        verify(commentService).addReactionTo(comment, likeType);
    }

    @Test
    void removeReactionToRemovesReactionToSpecifiedComment() throws Exception {
        final Comment comment = generateComment();
        when(commentService.findRequired(comment.getUri())).thenReturn(comment);
        final Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("testuser");
        mockMvc.perform(
                delete(PATH + NAME + "/reactions").queryParam(Constants.QueryParams.NAMESPACE, NAMESPACE)
                        .principal(principal)).andExpect(status().isNoContent());
        verify(commentService).removeMyReactionTo(comment);
    }
}
