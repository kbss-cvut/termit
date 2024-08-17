package cz.cvut.kbss.termit.util;

import org.springframework.core.io.ClassPathResource;

import java.util.Optional;

/**
 * Implementation of {@link TypeAwareResource} for files on classpath.
 */
public class TypeAwareClasspathResource extends ClassPathResource implements TypeAwareResource {

    private final String mediaType;

    public TypeAwareClasspathResource(String path, String mediaType) {
        super(path);
        this.mediaType = mediaType;
    }

    @Override
    public Optional<String> getMediaType() {
        return Optional.ofNullable(mediaType);
    }

    @Override
    public Optional<String> getFileExtension() {
        return getPath().contains(".") ? Optional.of(getPath().substring(getPath().lastIndexOf("."))) :
               Optional.empty();
    }
}
