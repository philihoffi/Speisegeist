package com.philipphofmann.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template for the "get-or-create" image caching pattern shared by
 * {@link RecipeImageService} and {@link IngredientImageService}: look up a
 * persisted image first, and only call the (slow, costly) image provider on
 * a cache miss.
 *
 * <p>Concurrent requests for the same id are coalesced: only the first caller
 * actually generates the image; other callers for the same id wait for that
 * result instead of triggering their own redundant (and billed) provider
 * call. This only de-duplicates within this JVM instance — it does not
 * protect against races across multiple backend instances.
 *
 * @param <E> the domain entity the image is generated for (e.g. Recipe, Ingredient)
 * @param <I> the persisted image entity (e.g. RecipeImage, IngredientImage)
 */
@Slf4j
public abstract class AbstractImageGenerationService<E, I> {

    protected final JpaRepository<I, UUID> repository;
    protected final OpenRouterService openRouterService;

    private final ConcurrentHashMap<UUID, CompletableFuture<I>> inFlight = new ConcurrentHashMap<>();

    @Value("${openrouter.image-size:1024x1024}")
    private String imageSize;

    @Value("${openrouter.image-quality:medium}")
    private String imageQuality;

    protected AbstractImageGenerationService(JpaRepository<I, UUID> repository, OpenRouterService openRouterService) {
        this.repository = repository;
        this.openRouterService = openRouterService;
    }

    /**
     * Returns the (possibly newly generated) image for the given id. Not
     * transactional: the OpenRouter call may be slow and should not hold a
     * DB connection.
     */
    public I getOrCreateImage(UUID id) {
        return repository.findById(id).orElseGet(() -> generateOnce(id));
    }

    /**
     * Ensures only one caller per id actually generates+stores the image;
     * concurrent callers for the same id wait on that result instead of
     * starting their own generation.
     */
    private I generateOnce(UUID id) {
        CompletableFuture<I> myFuture = new CompletableFuture<>();
        CompletableFuture<I> inFlightFuture = inFlight.putIfAbsent(id, myFuture);

        if (inFlightFuture != null) {
            log.debug("Bild für {} wird bereits generiert, warte auf das Ergebnis", id);
            return joinUnwrapped(inFlightFuture);
        }

        try {
            I image = generateAndStore(fetchEntity(id));
            myFuture.complete(image);
            return image;
        } catch (RuntimeException | Error e) {
            myFuture.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(id, myFuture);
        }
    }

    /** Unwraps {@link CompletionException} so waiting callers see the original exception, not a wrapper. */
    private I joinUnwrapped(CompletableFuture<I> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private I generateAndStore(E entity) {
        String prompt = buildPrompt(entity);
        log.debug("Generiere Bild für {}", describeEntity(entity));

        OpenRouterService.GeneratedImage generated = openRouterService.generateImage(prompt, imageSize, imageQuality, 1);

        return repository.save(newImage(entity, generated, prompt, openRouterService.getImageModel()));
    }

    /** Loads the source entity, throwing its usual not-found exception if it doesn't exist. */
    protected abstract E fetchEntity(UUID id);

    /** Builds the image-generation prompt from the entity. */
    protected abstract String buildPrompt(E entity);

    /** Short human-readable description of the entity, for logging. */
    protected abstract String describeEntity(E entity);

    /** Builds the (not-yet-persisted) image entity from the generation result. */
    protected abstract I newImage(E entity, OpenRouterService.GeneratedImage generated, String prompt, String sourceModel);
}
