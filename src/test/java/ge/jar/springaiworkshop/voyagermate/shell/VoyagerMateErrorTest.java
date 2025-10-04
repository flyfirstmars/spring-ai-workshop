package ge.jar.springaiworkshop.voyagermate.shell;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VoyagerMateError Exception Classification")
class VoyagerMateErrorTest {

    @Nested
    @DisplayName("Given HTTP Client Exceptions")
    class HttpClientExceptionTests {

        @Test
        @DisplayName("When 401 Unauthorized exception occurs, Then should create AuthenticationError")
        void shouldCreateAuthenticationErrorFor401() {
            var httpException = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid API key");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.AuthenticationError.class);

            var authError = (VoyagerMateError.AuthenticationError) error;
            assertThat(authError.details()).contains("Invalid API key");
            assertThat(authError.getUserMessage()).contains("Authentication Error");
            assertThat(authError.getSuggestedActions()).contains("AZURE_OPENAI_API_KEY");
        }

        @Test
        @DisplayName("When 403 Forbidden exception occurs, Then should create AuthorizationError")
        void shouldCreateAuthorizationErrorFor403() {
            var httpException = new HttpClientErrorException(HttpStatus.FORBIDDEN, "Access denied");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.AuthorizationError.class);

            var authzError = (VoyagerMateError.AuthorizationError) error;
            assertThat(authzError.resource()).isEqualTo("Azure OpenAI");
            assertThat(authzError.details()).contains("Access denied");
            assertThat(authzError.getUserMessage()).contains("Authorization Error");
        }

        @Test
        @DisplayName("When 404 Not Found exception occurs, Then should create ResourceNotFoundError")
        void shouldCreateResourceNotFoundErrorFor404() {
            var httpException = new HttpClientErrorException(HttpStatus.NOT_FOUND, "Deployment not found");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.ResourceNotFoundError.class);

            var notFoundError = (VoyagerMateError.ResourceNotFoundError) error;
            assertThat(notFoundError.resourceType()).isEqualTo("API endpoint");
            assertThat(notFoundError.getUserMessage()).contains("Resource Not Found");
        }

        @Test
        @DisplayName("When 429 Rate Limit exception occurs, Then should create RateLimitError")
        void shouldCreateRateLimitErrorFor429() {
            var httpException = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.RateLimitError.class);

            var rateLimitError = (VoyagerMateError.RateLimitError) error;
            assertThat(rateLimitError.limitType()).isEqualTo("API");
            assertThat(rateLimitError.getUserMessage()).contains("Rate Limit Exceeded");
        }

        @Test
        @DisplayName("When 400 Bad Request exception occurs, Then should create BadRequestError")
        void shouldCreateBadRequestErrorFor400() {
            var httpException = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid parameters");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.BadRequestError.class);

            var badRequestError = (VoyagerMateError.BadRequestError) error;
            assertThat(badRequestError.parameter()).isEqualTo("request");
            assertThat(badRequestError.getUserMessage()).contains("Bad Request");
        }
    }

    @Nested
    @DisplayName("Given HTTP Server Exceptions")
    class HttpServerExceptionTests {

        @Test
        @DisplayName("When 500 Internal Server Error occurs, Then should create ServerError")
        void shouldCreateServerErrorFor500() {
            var httpException = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.ServerError.class);

            var serverError = (VoyagerMateError.ServerError) error;
            assertThat(serverError.service()).isEqualTo("Azure OpenAI");
            assertThat(serverError.errorCode()).isEqualTo("500");
            assertThat(serverError.getUserMessage()).contains("Server Error");
        }

        @Test
        @DisplayName("When 503 Service Unavailable occurs, Then should create ServiceUnavailableError")
        void shouldCreateServiceUnavailableErrorFor503() {
            var httpException = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service down");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.ServiceUnavailableError.class);

            var serviceError = (VoyagerMateError.ServiceUnavailableError) error;
            assertThat(serviceError.service()).isEqualTo("Azure OpenAI");
            assertThat(serviceError.getUserMessage()).contains("Service Unavailable");
        }

        @Test
        @DisplayName("When 502 Bad Gateway occurs, Then should create GatewayError")
        void shouldCreateGatewayErrorFor502() {
            var httpException = new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "Gateway error");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.GatewayError.class);

            var gatewayError = (VoyagerMateError.GatewayError) error;
            assertThat(gatewayError.gatewayType()).isEqualTo("Azure OpenAI Gateway");
            assertThat(gatewayError.getUserMessage()).contains("Gateway Error");
        }

        @Test
        @DisplayName("When 504 Gateway Timeout occurs, Then should create GatewayError")
        void shouldCreateGatewayErrorFor504() {
            var httpException = new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "Timeout");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error).isInstanceOf(VoyagerMateError.GatewayError.class);

            var gatewayError = (VoyagerMateError.GatewayError) error;
            assertThat(gatewayError.details()).contains("Timeout");
        }
    }

    @Nested
    @DisplayName("Given Network and IO Exceptions")
    class NetworkAndIOExceptionTests {

        @Test
        @DisplayName("When ResourceAccessException occurs, Then should create NetworkError")
        void shouldCreateNetworkErrorForResourceAccess() {
            var networkException = new ResourceAccessException("Connection timeout");

            var error = VoyagerMateError.fromException(networkException);

            assertThat(error).isInstanceOf(VoyagerMateError.NetworkError.class);

            var networkError = (VoyagerMateError.NetworkError) error;
            assertThat(networkError.cause()).contains("Connection timeout");
            assertThat(networkError.getUserMessage()).contains("Network Error");
            assertThat(networkError.getSuggestedActions()).contains("internet connection");
        }

        @Test
        @DisplayName("When NoSuchFileException occurs, Then should create FileError")
        void shouldCreateFileErrorForMissingFile() {
            var fileException = new NoSuchFileException("/path/to/missing/file.txt");

            var error = VoyagerMateError.fromException(fileException);

            assertThat(error).isInstanceOf(VoyagerMateError.FileError.class);

            var fileError = (VoyagerMateError.FileError) error;
            assertThat(fileError.operation()).isEqualTo("read");
            assertThat(fileError.filePath()).isEqualTo("/path/to/missing/file.txt");
            assertThat(fileError.reason()).isEqualTo("File not found");
            assertThat(fileError.getUserMessage()).contains("File Error");
        }

        @Test
        @DisplayName("When IOException occurs, Then should create FileError")
        void shouldCreateFileErrorForIOException() {
            var ioException = new IOException("Permission denied");

            var error = VoyagerMateError.fromException(ioException);

            assertThat(error).isInstanceOf(VoyagerMateError.FileError.class);

            var fileError = (VoyagerMateError.FileError) error;
            assertThat(fileError.operation()).isEqualTo("access");
            assertThat(fileError.reason()).contains("Permission denied");
        }
    }

    @Nested
    @DisplayName("Given Validation Exceptions")
    class ValidationExceptionTests {

        @Test
        @DisplayName("When DateTimeParseException occurs, Then should create ValidationError")
        void shouldCreateValidationErrorForDateParsing() {
            var dateException = new DateTimeParseException("Invalid date format", "2023-13-45", 0);

            var error = VoyagerMateError.fromException(dateException);

            assertThat(error).isInstanceOf(VoyagerMateError.ValidationError.class);

            var validationError = (VoyagerMateError.ValidationError) error;
            assertThat(validationError.field()).isEqualTo("date format");
            assertThat(validationError.value()).isEqualTo("2023-13-45");
            assertThat(validationError.constraint()).isEqualTo("YYYY-MM-DD");
            assertThat(validationError.getUserMessage()).contains("Validation Error");
        }

        @Test
        @DisplayName("When JsonProcessingException occurs, Then should create ValidationError")
        void shouldCreateValidationErrorForJsonParsing() {
            var jsonException = new JsonProcessingException("Invalid JSON") {
            };

            var error = VoyagerMateError.fromException(jsonException);

            assertThat(error).isInstanceOf(VoyagerMateError.ValidationError.class);

            var validationError = (VoyagerMateError.ValidationError) error;
            assertThat(validationError.field()).isEqualTo("JSON format");
            assertThat(validationError.getUserMessage()).contains("Validation Error");
        }

        @Test
        @DisplayName("When IllegalArgumentException occurs, Then should create ValidationError")
        void shouldCreateValidationErrorForIllegalArgument() {
            var argException = new IllegalArgumentException("Invalid parameter value");

            var error = VoyagerMateError.fromException(argException);

            assertThat(error).isInstanceOf(VoyagerMateError.ValidationError.class);

            var validationError = (VoyagerMateError.ValidationError) error;
            assertThat(validationError.field()).isEqualTo("parameter");
        }
    }

    @Nested
    @DisplayName("Given Spring AI Specific Exceptions")
    class SpringAIExceptionTests {

        @Test
        @DisplayName("When RestClientException with quota message occurs, Then should create QuotaExceededError")
        void shouldCreateQuotaExceededErrorForQuotaMessage() {
            var restException = new RestClientException("API quota exceeded for this month");

            var error = VoyagerMateError.fromException(restException);

            assertThat(error).isInstanceOf(VoyagerMateError.QuotaExceededError.class);

            var quotaError = (VoyagerMateError.QuotaExceededError) error;
            assertThat(quotaError.quotaType()).isEqualTo("API");
            assertThat(quotaError.getUserMessage()).contains("Quota Exceeded");
        }

        @Test
        @DisplayName("When RestClientException with content filter message occurs, Then should create ContentFilterError")
        void shouldCreateContentFilterErrorForContentFilterMessage() {
            var restException = new RestClientException("Response blocked by content filter");

            var error = VoyagerMateError.fromException(restException);

            assertThat(error).isInstanceOf(VoyagerMateError.ContentFilterError.class);

            var contentError = (VoyagerMateError.ContentFilterError) error;
            assertThat(contentError.filterType()).isEqualTo("safety");
            assertThat(contentError.triggeredContent()).isEqualTo("user input");
            assertThat(contentError.getUserMessage()).contains("Content Filtered");
        }

        @Test
        @DisplayName("When generic RestClientException occurs, Then should create UnknownError")
        void shouldCreateUnknownErrorForGenericRestException() {
            var restException = new RestClientException("Some unknown REST error");

            var error = VoyagerMateError.fromException(restException);

            assertThat(error).isInstanceOf(VoyagerMateError.UnknownError.class);

            var unknownError = (VoyagerMateError.UnknownError) error;
            assertThat(unknownError.originalMessage()).contains("Some unknown REST error");
            assertThat(unknownError.exceptionType()).isEqualTo("RestClientException");
        }
    }

    @Nested
    @DisplayName("Given Unknown Exceptions")
    class UnknownExceptionTests {

        @Test
        @DisplayName("When unknown exception occurs, Then should create UnknownError")
        void shouldCreateUnknownErrorForUnknownException() {
            var unknownException = new RuntimeException("Unexpected error");

            var error = VoyagerMateError.fromException(unknownException);

            assertThat(error).isInstanceOf(VoyagerMateError.UnknownError.class);

            var unknownError = (VoyagerMateError.UnknownError) error;
            assertThat(unknownError.originalMessage()).isEqualTo("Unexpected error");
            assertThat(unknownError.exceptionType()).isEqualTo("RuntimeException");
            assertThat(unknownError.getUserMessage()).contains("Unexpected Error");
        }
    }

    @Nested
    @DisplayName("Given Error Interface Contracts")
    class ErrorInterfaceTests {

        @Test
        @DisplayName("When any error is created, Then should have user message, technical details, and suggested actions")
        void shouldHaveRequiredErrorInformation() {
            var httpException = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid API key");

            var error = VoyagerMateError.fromException(httpException);

            assertThat(error.getUserMessage())
                    .isNotNull()
                    .isNotBlank();

            assertThat(error.getTechnicalDetails())
                    .isNotNull()
                    .isNotBlank();

            assertThat(error.getSuggestedActions())
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("When NetworkError is created with specific data, Then should provide contextual information")
        void shouldProvideContextualInformationForNetworkError() {
            var endpoint = "api.openai.azure.com";
            var cause = "Connection timeout after 30s";

            var networkError = new VoyagerMateError.NetworkError(endpoint, cause);

            assertThat(networkError.endpoint()).isEqualTo(endpoint);
            assertThat(networkError.cause()).isEqualTo(cause);
            assertThat(networkError.getTechnicalDetails()).contains(endpoint, cause);
            assertThat(networkError.getUserMessage()).contains("Network Error");
            assertThat(networkError.getSuggestedActions()).contains("internet connection");
        }

        @Test
        @DisplayName("When RateLimitError is created with retry information, Then should provide retry guidance")
        void shouldProvideRetryGuidanceForRateLimitError() {
            var limitType = "requests per minute";
            var retryAfter = "60 seconds";

            var rateLimitError = new VoyagerMateError.RateLimitError(limitType, retryAfter);

            assertThat(rateLimitError.limitType()).isEqualTo(limitType);
            assertThat(rateLimitError.retryAfter()).isEqualTo(retryAfter);
            assertThat(rateLimitError.getTechnicalDetails()).contains(limitType, retryAfter);
            assertThat(rateLimitError.getSuggestedActions()).contains(retryAfter);
        }
    }
}
