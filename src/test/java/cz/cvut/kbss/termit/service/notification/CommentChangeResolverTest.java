package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentChangeResolverTest {

    @Mock
    private CommentService commentService;

    @Mock
    private TermService termService;

    @InjectMocks
    private CommentChangeResolver sut;

    @Test
    void resolveCommentsRetrievesCommentsInIntervalAndMapsThemByTerm() {
        final List<Term> terms = List.of(Generator.generateTermWithId(), Generator.generateTermWithId());
        final Map<Term, List<Comment>> expected = new HashMap<>();
        terms.forEach(t -> {
            when(termService.find(t.getUri())).thenReturn(Optional.of(t));
            expected.put(t, Generator.generateComments(t));
        });
        final Instant from = Utils.timestamp().minus(5, ChronoUnit.DAYS);
        final Instant to = Utils.timestamp();
        when(commentService.findAll(null, from, to)).thenReturn(
                expected.values().stream().flatMap(Collection::stream).collect(
                        Collectors.toList()));

        final Map<Asset<?>, List<Comment>> result = sut.resolveComments(from, to);
        assertEquals(expected, result);
        verify(commentService).findAll(null, from, to);
        terms.forEach(t -> verify(termService).find(t.getUri()));
    }
}
