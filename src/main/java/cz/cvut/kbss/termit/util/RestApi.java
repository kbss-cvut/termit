package cz.cvut.kbss.termit.util;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.*;

import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping(REST_MAPPING_PATH)
public @interface RestApi {

    @AliasFor(annotation = RequestMapping.class)
    String value() default "";
}
