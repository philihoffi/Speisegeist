package com.philipphofmann.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the concurrent request-coalescing behavior of {@link AbstractImageGenerationService}
 * itself, independent of any concrete entity type — {@link RecipeImageServiceTest} and
 * {@link IngredientImageServiceTest} cover the sequential get-or-create/prompt-building behavior.
 */
@ExtendWith(MockitoExtension.class)
class AbstractImageGenerationServiceTest {

    @Mock private JpaRepository<String, UUID> repository;
    @Mock private OpenRouterService openRouterService;

    private TestImageGenerationService service;
    private UUID id;

    @BeforeEach
    void setUp() {
        service = new TestImageGenerationService(repository, openRouterService);
        ReflectionTestUtils.setField(service, "imageSize", "1024x1024");
        ReflectionTestUtils.setField(service, "imageQuality", "medium");
        id = UUID.randomUUID();
    }

    @Test
    void coalescesConcurrentGenerationForTheSameId() throws Exception {
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        when(openRouterService.generateImage(any(), any(), any(), any())).thenAnswer(inv -> {
            entered.countDown();
            assertThat(proceed.await(2, TimeUnit.SECONDS)).isTrue();
            return new OpenRouterService.GeneratedImage(new byte[]{7}, MediaType.IMAGE_PNG_VALUE);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> service.getOrCreateImage(id));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            // second caller arrives while the first is still generating
            Future<String> second = pool.submit(() -> service.getOrCreateImage(id));
            Thread.sleep(200); // give it time to join the in-flight future before we unblock it
            proceed.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS)).isSameAs(second.get(2, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        verify(openRouterService, times(1)).generateImage(any(), any(), any(), any());
        verify(repository, times(1)).save(any());
    }

    @Test
    void releasesWaitingCallersWhenGenerationFails() throws Exception {
        when(repository.findById(id)).thenReturn(Optional.empty());

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        when(openRouterService.generateImage(any(), any(), any(), any())).thenAnswer(inv -> {
            entered.countDown();
            assertThat(proceed.await(2, TimeUnit.SECONDS)).isTrue();
            throw new IllegalStateException("boom");
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> service.getOrCreateImage(id));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<String> second = pool.submit(() -> service.getOrCreateImage(id));
            Thread.sleep(200);
            proceed.countDown();

            assertThatThrownBy(() -> first.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            pool.shutdownNow();
        }

        verify(openRouterService, times(1)).generateImage(any(), any(), any(), any());
    }

    private static class TestImageGenerationService extends AbstractImageGenerationService<UUID, String> {
        TestImageGenerationService(JpaRepository<String, UUID> repository, OpenRouterService openRouterService) {
            super(repository, openRouterService);
        }

        @Override
        protected UUID fetchEntity(UUID id) {
            return id;
        }

        @Override
        protected String buildPrompt(UUID id) {
            return "prompt for " + id;
        }

        @Override
        protected String describeEntity(UUID id) {
            return "entity " + id;
        }

        @Override
        protected String newImage(UUID id, OpenRouterService.GeneratedImage generated, String prompt, String sourceModel) {
            return "image-for-" + id;
        }
    }
}
