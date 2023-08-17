package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleCacheTest {

    @Mock
    Function<URI, Set<TermInfo>> supplier;

    private final SimpleCache<URI, Set<TermInfo>> sut = new SimpleCache<>();

    @Test
    void getOrComputeReturnsValueComputedUsingSpecifiedSupplierWhenKeyIsNotPresent() {
        final Set<TermInfo> data = generateData();
        final URI key = Generator.generateUri();
        when(supplier.apply(any(URI.class))).thenReturn(data);

        final Set<TermInfo> result = sut.getOrCompute(key, supplier);
        assertEquals(data, result);
        verify(supplier).apply(key);
    }

    private Set<TermInfo> generateData() {
        return IntStream.range(0, 5).mapToObj(i -> Generator.generateTermInfoWithId()).collect(Collectors.toSet());
    }

    @Test
    void getOrComputeReturnsCachedValue() {
        final Set<TermInfo> data = generateData();
        final URI key = Generator.generateUri();
        when(supplier.apply(any(URI.class))).thenReturn(data);

        final Set<TermInfo> resultOne = sut.getOrCompute(key, supplier);
        assertEquals(data, resultOne);
        final Set<TermInfo> resultTwo = sut.getOrCompute(key, supplier);
        assertEquals(data, resultTwo);
        // Expect one invocation only
        verify(supplier).apply(key);
    }

    @Test
    void evictRemovesCachedValueForSpecifiedKey() {
        final Set<TermInfo> data = generateData();
        final URI key = Generator.generateUri();
        when(supplier.apply(any(URI.class))).thenReturn(data);

        final Set<TermInfo> resultOne = sut.getOrCompute(key, supplier);
        assertEquals(data, resultOne);
        sut.evict(key);
        final Set<TermInfo> resultTwo = sut.getOrCompute(key, supplier);
        assertEquals(data, resultTwo);
        verify(supplier, times(2)).apply(key);
    }

    @Test
    void evictRemovesCachedValueOnlyForSpecifiedKey() {
        final Set<TermInfo> data = generateData();
        final URI key = Generator.generateUri();
        final URI keyTwo = Generator.generateUri();
        when(supplier.apply(any(URI.class))).thenReturn(data);

        assertEquals(data, sut.getOrCompute(key, supplier));
        assertEquals(data, sut.getOrCompute(keyTwo, supplier));
        sut.evict(key);
        assertEquals(data, sut.getOrCompute(key, supplier));
        assertEquals(data, sut.getOrCompute(keyTwo, supplier));
        verify(supplier, times(2)).apply(key);
        // Key two was not evicted
        verify(supplier).apply(keyTwo);
    }

    @Test
    void evictAllClearsWholeCache() {
        final Set<TermInfo> data = generateData();
        final URI key = Generator.generateUri();
        final URI keyTwo = Generator.generateUri();
        when(supplier.apply(any(URI.class))).thenReturn(data);

        assertEquals(data, sut.getOrCompute(key, supplier));
        assertEquals(data, sut.getOrCompute(keyTwo, supplier));
        sut.evictAll();
        assertEquals(data, sut.getOrCompute(key, supplier));
        assertEquals(data, sut.getOrCompute(keyTwo, supplier));
        verify(supplier, times(2)).apply(key);
        verify(supplier, times(2)).apply(keyTwo);
    }
}
