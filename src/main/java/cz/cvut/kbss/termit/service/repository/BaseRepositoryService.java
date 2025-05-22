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
package cz.cvut.kbss.termit.service.repository;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import cz.cvut.kbss.termit.exception.InvalidIdentifierException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.validation.ValidationResult;
import jakarta.annotation.Nonnull;
import jakarta.validation.Validator;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base implementation of repository services.
 * <p>
 * It contains the basic transactional CRUD operations. Subclasses are expected to provide DAO of the correct type,
 * which is used by the CRUD methods implemented by this base class.
 * <p>
 * In order to minimize chances of messing up the transactional behavior, subclasses *should not* override the main CRUD
 * methods and instead should provide custom business logic by overriding the helper hooks such as {@link
 * #prePersist(HasIdentifier)}.
 *
 * @param <T> Domain object type managed by this service
 */
public abstract class BaseRepositoryService<T extends HasIdentifier, DTO extends HasIdentifier> {

    private final Validator validator;

    protected BaseRepositoryService(Validator validator) {
        this.validator = validator;
    }

    /**
     * Gets primary DAO which is used to implement the CRUD methods in this service.
     *
     * @return Data access object
     */
    protected abstract GenericDao<T> getPrimaryDao();

    // Read methods are intentionally not transactional because, for example, when postLoad manipulates the resulting
    // entity in any way, transaction commit would attempt to insert the change into the repository, which is not desired

    /**
     * Loads all instances of the type managed by this service from the repository.
     *
     * @return List of all matching instances
     */
    public List<DTO> findAll() {
        final List<T> loaded = getPrimaryDao().findAll();
        return loaded.stream().map(this::postLoad).map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * Maps the specified entity to a DTO used for listing large number of objects of the entity's type.
     *
     * @param entity Entity to map
     * @return DTO representing the entity
     */
    protected abstract DTO mapToDto(T entity);

    /**
     * Finds an object with the specified id and returns it.
     *
     * @param id Identifier of the object to load
     * @return {@link Optional} with the loaded object or an empty one
     * @see #findRequired(URI)
     */
    public Optional<T> find(URI id) {
        return getPrimaryDao().find(id).map(this::postLoad);
    }

    /**
     * Gets a reference to an object wih the specified identifier.
     * <p>
     * Note that all attributes of the reference are loaded lazily and the corresponding persistence context must be
     * still open to load them.
     * <p>
     * Also note that, in contrast to {@link #find(URI)}, this method does not invoke {@link #postLoad(HasIdentifier)}
     * for the loaded instance.
     *
     * @param id Identifier of the object to load
     * @return Entity reference
     * @throws NotFoundException If no matching instance is found
     */
    public T getReference(URI id) {
        if (exists(id)) {
            return getPrimaryDao().getReference(id);
        }
        throw NotFoundException.create(resolveGenericType().getSimpleName(), id);
    }

    /**
     * Finds an object with the specified id and returns it.
     * <p>
     * In comparison to {@link #find(URI)}, this method guarantees to return a matching instance. If no such object is
     * found, a {@link NotFoundException} is thrown.
     *
     * @param id Identifier of the object to load
     * @return The matching object
     * @throws NotFoundException If no matching instance is found
     * @see #find(URI)
     */
    public T findRequired(URI id) {
        return find(id).orElseThrow(() -> NotFoundException.create(resolveGenericType().getSimpleName(), id));
    }

    /**
     * Resolves the actual generic type of the implementation of {@link BaseRepositoryService}.
     *
     * @return Actual generic type class
     */
    private Class<T> resolveGenericType() {
        // Adapted from https://gist.github.com/yunspace/930d4d40a787a1f6a7d1
        final List<ResolvedType> typeParameters =
                new TypeResolver().resolve(this.getClass()).typeParametersFor(BaseRepositoryService.class);
        assert !typeParameters.isEmpty();
        return (Class<T>) typeParameters.get(0).getErasedType();
    }

    /**
     * Override this method to plug custom behavior into {@link #find(URI)} or {@link #findAll()}.
     *
     * @param instance The loaded instance, not {@code null}
     */
    protected T postLoad(@Nonnull T instance) {
        // Do nothing
        return instance;
    }

    /**
     * Persists the specified instance into the repository.
     *
     * @param instance The instance to persist
     */
    @Transactional
    public void persist(@Nonnull T instance) {
        Objects.requireNonNull(instance);
        prePersist(instance);
        getPrimaryDao().persist(instance);
        postPersist(instance);
    }

    /**
     * Override this method to plug custom behavior into the transactional cycle of {@link #persist(HasIdentifier)}.
     * <p>
     * The default behavior is to validate the specified instance.
     *
     * @param instance The instance to be persisted, not {@code null}
     */
    protected void prePersist(@Nonnull T instance) {
        validate(instance);
        validateUri(instance.getUri());
    }

    /**
     * Override this method to plug custom behavior into the transactional cycle of {@link #persist(HasIdentifier)}.
     *
     * @param instance The persisted instance, not {@code null}
     */
    protected void postPersist(@Nonnull T instance) {
        // Do nothing
    }

    /**
     * Merges the specified updated instance into the repository.
     *
     * @param instance The instance to merge
     * @throws NotFoundException If the entity does not exist in the repository
     */
    @Transactional
    public T update(T instance) {
        Objects.requireNonNull(instance);
        preUpdate(instance);
        final T result = getPrimaryDao().update(instance);
        assert result != null;
        postUpdate(result);
        return result;
    }

    /**
     * Override this method to plug custom behavior into the transactional cycle of {@link #update(HasIdentifier)} )}.
     * <p>
     * The default behavior is to validate the specified instance and ensure its existence in the repository.
     *
     * @param instance The instance to be updated, not {@code null}
     */
    protected void preUpdate(@Nonnull T instance) {
        validateUri(instance.getUri());
        if (!exists(instance.getUri())) {
            throw NotFoundException.create(instance.getClass().getSimpleName(), instance.getUri());
        }
        validate(instance);
    }

    /**
     * Override this method to plug custom behavior into the transactional cycle of {@link #update(HasIdentifier)}.
     *
     * @param instance The updated instance which will be returned by {@link #update(HasIdentifier)}, not {@code null}
     */
    protected void postUpdate(@Nonnull T instance) {
        // Do nothing
    }

    /**
     * Removes the specified instance from the repository.
     *
     * @param instance The instance to remove
     */
    @Transactional
    public void remove(T instance) {
        Objects.requireNonNull(instance);
        preRemove(instance);
        getPrimaryDao().remove(instance);
        postRemove(instance);
    }

    /**
     * Override this method to plug custom behavior into the transactional cycle of {@link #remove(HasIdentifier)}.
     * <p>
     * The default behavior is a no-op.
     *
     * @param instance The instance to be removed, not {@code null}
     */
    protected void preRemove(@Nonnull T instance) {
        // Do nothing
    }

    /**
     * Override this method to plug custom behavior into the transactional cycle of {@link #remove(HasIdentifier)}.
     * <p>
     * The default behavior is a no-op.
     *
     * @param instance The removed instance, not {@code null}
     */
    protected void postRemove(@Nonnull T instance) {
        // Do nothing
    }

    /**
     * Checks whether an instance with the specified identifier exists in the repository.
     *
     * @param id ID to check
     * @return {@code true} if the instance exists, {@code false} otherwise
     */
    public boolean exists(URI id) {
        return getPrimaryDao().exists(id);
    }

    /**
     * Validates the specified instance, using JSR 380.
     * <p>
     * This assumes that the type contains JSR 380 validation annotations.
     *
     * @param instance The instance to validate
     * @throws ValidationException In case the instance is not valid
     */
    protected void validate(T instance) {
        final ValidationResult<T> validationResult = ValidationResult.of(validator.validate(instance));
        if (!validationResult.isValid()) {
            throw new ValidationException(validationResult);
        }
    }

    /**
     * Validates the specified uri.
     *
     * @param uri the uri to validate
     * @throws cz.cvut.kbss.termit.exception.InvalidIdentifierException when the URI is invalid
     * @see cz.cvut.kbss.termit.service.IdentifierResolver#isUri(String)
     */
    protected void validateUri(URI uri) throws InvalidIdentifierException {
        if (uri != null && !IdentifierResolver.isUri(uri.toString())) {
            throw new InvalidIdentifierException("Invalid URI: '" + uri + "'", "error.invalidIdentifier").addParameter("uri", uri.toString());
        }
    }
}
