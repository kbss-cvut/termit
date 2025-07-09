/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.model.util.validation.HasPrimaryLanguage;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultilingualStringPrimaryNotBlankValidatorTest {
    private static final String LANGUAGE = "testLanguage";

    @Mock
    private PrimaryNotBlank annotationInstance;

    @Mock
    private ConstraintValidatorContext validatorContext;

    @InjectMocks
    private MultilingualStringPrimaryNotBlankValidator sut;

    @BeforeEach
    void setUp() throws Exception {
        resetStaticReflectionCache();
        when(annotationInstance.value()).thenReturn(new String[]{"fieldToValidate"});
        sut.initialize(annotationInstance);
    }

    @Test
    void isValidReturnsFalseForNullValue() {
        assertFalse(sut.isValid(null, validatorContext));
    }

    @Test
    void isValidReturnsFalseWhenValueDoesNotContainPrimaryTranslation() {
        final BeanToValidate bean = new BeanToValidate();
        bean.setPrimaryLanguage(LANGUAGE);
        bean.setFieldToValidate(MultilingualString.create("test", "en"));
        bean.setFieldToNotValidate(MultilingualString.create("test", LANGUAGE));

        assertFalse(sut.isValid(bean, validatorContext));
    }

    @Test
    void isValidReturnsTrueWhenValueContainsPrimaryTranslation() {
        final BeanToValidate bean = new BeanToValidate();
        bean.setPrimaryLanguage(LANGUAGE);
        bean.setFieldToValidate(MultilingualString.create("test", LANGUAGE));
        bean.setFieldToNotValidate(MultilingualString.create("test", "en"));
        assertTrue(sut.isValid(bean, validatorContext));
    }

    @Test
    void isValidReturnsFalseWhenFieldIsNull() {
        final BeanToValidate bean = new BeanToValidate();
        bean.setPrimaryLanguage(LANGUAGE);
        bean.setFieldToValidate(null);
        bean.setFieldToNotValidate(MultilingualString.create("test", "en"));
        assertFalse(sut.isValid(bean, validatorContext));
    }

    @Test
    void isValidThrowsWhenAnnotatedClassIsMissingSpecifiedField() {
        reset(annotationInstance);
        when(annotationInstance.value()).thenReturn(new String[]{"nonExistentField"});
        sut.initialize(annotationInstance);
        assertThrows(IllegalArgumentException.class, () ->
            sut.isValid(new BeanToValidate(), validatorContext)
        );
    }

    /**
     * Resolves the cache map {@link MultilingualStringPrimaryNotBlankValidator#CACHE}
     * and clears its contents.
     */
    @SuppressWarnings("rawtypes")
    private void resetStaticReflectionCache() throws NoSuchFieldException, IllegalAccessException {
        final Class<?> validatorClass = MultilingualStringPrimaryNotBlankValidator.class;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        lookup = MethodHandles.privateLookupIn(validatorClass, lookup);
        final ConcurrentMap cacheMap = (ConcurrentMap) lookup.findStaticVarHandle(
                validatorClass, "CACHE", ConcurrentMap.class).get();
        cacheMap.clear();
    }

    @PrimaryNotBlank({"fieldToValidate"})
    public static class BeanToValidate implements HasPrimaryLanguage {
        private String primaryLanguage;
        private MultilingualString fieldToValidate;
        private MultilingualString fieldToNotValidate;

        @Override
        public String getPrimaryLanguage() {
            return primaryLanguage;
        }

        public void setPrimaryLanguage(String primaryLanguage) {
            this.primaryLanguage = primaryLanguage;
        }

        public MultilingualString getFieldToValidate() {
            return fieldToValidate;
        }

        public void setFieldToValidate(MultilingualString fieldToValidate) {
            this.fieldToValidate = fieldToValidate;
        }

        public MultilingualString getFieldToNotValidate() {
            return fieldToNotValidate;
        }

        public void setFieldToNotValidate(MultilingualString fieldToNotValidate) {
            this.fieldToNotValidate = fieldToNotValidate;
        }
    }
}
