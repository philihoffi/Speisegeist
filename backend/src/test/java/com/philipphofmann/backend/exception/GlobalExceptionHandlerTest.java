package com.philipphofmann.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that every exception type maps to the intended HTTP status and that
 * internal details are never leaked to the client.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void recipeNotFound_maps404() {
        var response = handler.handleNotFound(new RecipeNotFoundException(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void ingredientNotFound_maps404() {
        var response = handler.handleIngredientNotFound(new IngredientNotFoundException(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void emailAlreadyExists_maps409() {
        var response = handler.handleEmailExists(new EmailAlreadyExistsException("E-Mail vergeben"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("E-Mail vergeben");
    }

    @Test
    void authException_maps401() {
        var response = handler.handleAuth(new AuthException("Ungültige Zugangsdaten"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Ungültige Zugangsdaten");
    }

    @Test
    void openRouterUnavailable_maps503AndHidesInternalMessage() {
        var response = handler.handleOpenRouterUnavailable(
                new OpenRouterUnavailableException("Interner Stacktrace mit API-Key-Details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).doesNotContain("API-Key");
        assertThat(response.getBody().message()).contains("KI-Dienst");
    }

    @Test
    void recipeGeneration_maps502AndHidesInternalMessage() {
        var response = handler.handleGeneration(
                new RecipeGenerationException("Roh-JSON der KI mit sensiblen Daten"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().message()).doesNotContain("Roh-JSON");
    }

    @Test
    void dataIntegrityViolation_maps409() {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("constraint uk_ingredient_name violated"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // The raw SQL constraint name must not reach the client.
        assertThat(response.getBody().message()).doesNotContain("uk_ingredient_name");
    }

    @Test
    void validationError_maps400WithFirstFieldMessage() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "recipeRequest");
        bindingResult.rejectValue(null, "NotBlank", "darf nicht leer sein");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "recipeRequest", "name", "darf nicht leer sein"));

        var exception = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);
        var response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("name: darf nicht leer sein");
    }

    @Test
    void validationError_withoutFieldErrors_usesFallbackMessage() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "recipeRequest");

        var exception = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);
        var response = handler.handleValidation(exception);

        assertThat(response.getBody().message()).isEqualTo("Ungültige Eingabe");
    }

    @Test
    void unexpectedException_maps500AndHidesDetails() {
        var response = handler.handleGeneric(
                new NullPointerException("com.philipphofmann.internal.Foo.bar() is null"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Interner Serverfehler");
    }

    @Test
    void everyResponse_carriesATimestamp() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleAuth(new AuthException("x"));

        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
