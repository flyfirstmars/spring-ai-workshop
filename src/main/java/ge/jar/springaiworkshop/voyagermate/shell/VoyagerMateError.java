package ge.jar.springaiworkshop.voyagermate.shell;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.format.DateTimeParseException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

public sealed interface VoyagerMateError
        permits VoyagerMateError.NetworkError,
        VoyagerMateError.AuthenticationError,
        VoyagerMateError.AuthorizationError,
        VoyagerMateError.ResourceNotFoundError,
        VoyagerMateError.RateLimitError,
        VoyagerMateError.BadRequestError,
        VoyagerMateError.ServerError,
        VoyagerMateError.ServiceUnavailableError,
        VoyagerMateError.GatewayError,
        VoyagerMateError.TimeoutError,
        VoyagerMateError.QuotaExceededError,
        VoyagerMateError.ContentFilterError,
        VoyagerMateError.ModelError,
        VoyagerMateError.FileError,
        VoyagerMateError.ValidationError,
        VoyagerMateError.ConfigurationError,
        VoyagerMateError.UnknownError {

    String getUserMessage();

    String getTechnicalDetails();

    String getSuggestedActions();

    record NetworkError(String endpoint, String cause) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Network Error: Unable to connect to Azure OpenAI service";
        }

        @Override
        public String getTechnicalDetails() {
            return "Failed to connect to endpoint: " + endpoint + ", cause: " + cause;
        }

        @Override
        public String getSuggestedActions() {
            return "Check your internet connection and API endpoint configuration";
        }
    }

    record AuthenticationError(String details) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Authentication Error: Invalid API key or credentials";
        }

        @Override
        public String getTechnicalDetails() {
            return "Authentication failed: " + details;
        }

        @Override
        public String getSuggestedActions() {
            return "Verify your AZURE_OPENAI_API_KEY environment variable is set correctly";
        }
    }

    record AuthorizationError(String resource, String details) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Authorization Error: Access forbidden";
        }

        @Override
        public String getTechnicalDetails() {
            return "Access denied to resource: " + resource + ", details: " + details;
        }

        @Override
        public String getSuggestedActions() {
            return "Check your Azure OpenAI deployment permissions and quotas";
        }
    }

    record ResourceNotFoundError(String resourceType, String resourceName) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Resource Not Found: " + resourceType + " not found";
        }

        @Override
        public String getTechnicalDetails() {
            return "Resource not found - type: " + resourceType + ", name: " + resourceName;
        }

        @Override
        public String getSuggestedActions() {
            return "Verify your " + resourceType + " configuration and deployment settings";
        }
    }

    record RateLimitError(String limitType, String retryAfter) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Rate Limit Exceeded: Too many " + limitType + " requests";
        }

        @Override
        public String getTechnicalDetails() {
            return "Rate limit exceeded for: " + limitType + ", retry after: " + retryAfter;
        }

        @Override
        public String getSuggestedActions() {
            return "Please wait " + (retryAfter != null ? retryAfter : "a moment") + " and try again";
        }
    }

    record BadRequestError(String parameter, String value, String reason) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Bad Request: Invalid " + parameter;
        }

        @Override
        public String getTechnicalDetails() {
            return "Invalid parameter: " + parameter + ", value: " + value + ", reason: " + reason;
        }

        @Override
        public String getSuggestedActions() {
            return "Check your " + parameter + " and ensure it meets the required format";
        }
    }

    record ServerError(String service, String errorCode) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Server Error: " + service + " experiencing issues";
        }

        @Override
        public String getTechnicalDetails() {
            return "Server error in service: " + service + ", error code: " + errorCode;
        }

        @Override
        public String getSuggestedActions() {
            return "This is usually temporary. Please try again in a few moments";
        }
    }

    record ServiceUnavailableError(String service, String estimatedRecovery) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Service Unavailable: " + service + " is temporarily down";
        }

        @Override
        public String getTechnicalDetails() {
            return "Service unavailable: " + service + ", estimated recovery: " + estimatedRecovery;
        }

        @Override
        public String getSuggestedActions() {
            return "Please wait and try again" +
                    (estimatedRecovery != null ? " (estimated recovery: " + estimatedRecovery + ")" : " later");
        }
    }

    record GatewayError(String gatewayType, String details) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Gateway Error: Connection issue with Azure OpenAI";
        }

        @Override
        public String getTechnicalDetails() {
            return "Gateway error: " + gatewayType + ", details: " + details;
        }

        @Override
        public String getSuggestedActions() {
            return "The service may be temporarily unavailable. Please retry";
        }
    }

    record TimeoutError(String operation, long timeoutMs) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Timeout: " + operation + " took too long to complete";
        }

        @Override
        public String getTechnicalDetails() {
            return "Operation timed out: " + operation + ", timeout: " + timeoutMs + "ms";
        }

        @Override
        public String getSuggestedActions() {
            return "Try reducing your request size or complexity";
        }
    }

    record QuotaExceededError(String quotaType, String currentUsage, String limit) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Quota Exceeded: " + quotaType + " quota has been reached";
        }

        @Override
        public String getTechnicalDetails() {
            return "Quota exceeded - type: " + quotaType + ", usage: " + currentUsage + ", limit: " + limit;
        }

        @Override
        public String getSuggestedActions() {
            return "Check your Azure OpenAI usage and consider upgrading your tier";
        }
    }

    record ContentFilterError(String filterType, String triggeredContent) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Content Filtered: Your request was blocked by safety filters";
        }

        @Override
        public String getTechnicalDetails() {
            return "Content filter triggered - type: " + filterType + ", content: " + triggeredContent;
        }

        @Override
        public String getSuggestedActions() {
            return "Please rephrase your request to avoid potentially harmful content";
        }
    }

    record ModelError(String modelName, String issue) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Model Error: Issue with " + modelName;
        }

        @Override
        public String getTechnicalDetails() {
            return "Model error - name: " + modelName + ", issue: " + issue;
        }

        @Override
        public String getSuggestedActions() {
            return "Try again or check if the model deployment is available";
        }
    }

    record FileError(String operation, String filePath, String reason) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "File Error: Cannot " + operation + " file";
        }

        @Override
        public String getTechnicalDetails() {
            return "File operation failed - operation: " + operation + ", path: " + filePath + ", reason: " + reason;
        }

        @Override
        public String getSuggestedActions() {
            return "Check file path and permissions: " + filePath;
        }
    }

    record ValidationError(String field, String value, String constraint) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Validation Error: Invalid " + field;
        }

        @Override
        public String getTechnicalDetails() {
            return "Validation failed - field: " + field + ", value: " + value + ", constraint: " + constraint;
        }

        @Override
        public String getSuggestedActions() {
            return "Ensure " + field + " meets the requirement: " + constraint;
        }
    }

    record ConfigurationError(String component, String missingConfig) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Configuration Error: " + component + " not properly configured";
        }

        @Override
        public String getTechnicalDetails() {
            return "Configuration issue - component: " + component + ", missing: " + missingConfig;
        }

        @Override
        public String getSuggestedActions() {
            return "Please configure " + missingConfig + " for " + component;
        }
    }

    record UnknownError(String originalMessage, String exceptionType) implements VoyagerMateError {
        @Override
        public String getUserMessage() {
            return "Unexpected Error: Something went wrong";
        }

        @Override
        public String getTechnicalDetails() {
            return "Unknown error - type: " + exceptionType + ", message: " + originalMessage;
        }

        @Override
        public String getSuggestedActions() {
            return "Please try again or contact support if the issue persists";
        }
    }

    static VoyagerMateError fromException(Exception ex) {
        return switch (ex) {
            case ResourceAccessException rae -> new NetworkError(
                    extractEndpoint(rae.getMessage()),
                    rae.getCause() != null ? rae.getCause().getMessage() : rae.getMessage()
            );

            case HttpClientErrorException hcee when hcee.getStatusCode().value() == 401 ->
                    new AuthenticationError(hcee.getMessage());

            case HttpClientErrorException hcee when hcee.getStatusCode().value() == 403 ->
                    new AuthorizationError("Azure OpenAI", hcee.getMessage());

            case HttpClientErrorException hcee when hcee.getStatusCode().value() == 404 ->
                    new ResourceNotFoundError("API endpoint", extractResource(hcee.getMessage()));

            case HttpClientErrorException hcee when hcee.getStatusCode().value() == 429 ->
                    new RateLimitError("API", extractRetryAfter(hcee.getResponseHeaders()));

            case HttpClientErrorException hcee when hcee.getStatusCode().value() == 400 ->
                    new BadRequestError("request", "", hcee.getMessage());

            case HttpClientErrorException hcee when hcee.getStatusCode().value() == 503 ->
                    new ServiceUnavailableError("Azure OpenAI", null);

            case HttpServerErrorException hsee when hsee.getStatusCode().value() == 500 ->
                    new ServerError("Azure OpenAI", String.valueOf(hsee.getStatusCode().value()));

            case HttpServerErrorException hsee when hsee.getStatusCode().value() == 503 ->
                    new ServiceUnavailableError("Azure OpenAI", null);

            case HttpServerErrorException hsee when (hsee.getStatusCode().value() == 502 ||
                    hsee.getStatusCode().value() == 504) -> new GatewayError("Azure OpenAI Gateway", hsee.getMessage());

            case TransientAiException ignored -> new ServerError("Spring AI", "Transient error");

            case NonTransientAiException ntae -> new ConfigurationError("Spring AI", ntae.getMessage());

            case RestClientException rce when rce.getMessage().contains("quota") ->
                    new QuotaExceededError("API", "unknown", "unknown");

            case RestClientException rce when rce.getMessage().contains("content filter") ->
                    new ContentFilterError("safety", "user input");

            case NoSuchFileException nsfe -> new FileError("read", nsfe.getFile(), "File not found");

            case JsonProcessingException jpe -> new ValidationError("JSON format", "", jpe.getMessage());

            case DateTimeParseException dtpe ->
                    new ValidationError("date format", dtpe.getParsedString(), "YYYY-MM-DD");

            case IOException ioe -> new FileError("access", "unknown", ioe.getMessage());

            case IllegalArgumentException iae -> new ValidationError("parameter", "", iae.getMessage());

            case RestClientException rce -> new UnknownError(rce.getMessage(), "RestClientException");

            default -> new UnknownError(ex.getMessage(), ex.getClass().getSimpleName());
        };
    }

    private static String extractEndpoint(String message) {
        return message != null ? message : "unknown";
    }

    private static String extractResource(String message) {
        return message != null ? message : "unknown";
    }

    private static String extractRetryAfter(org.springframework.http.HttpHeaders headers) {
        if (headers != null && headers.containsKey("Retry-After")) {
            return headers.getFirst("Retry-After") + " seconds";
        }
        return "a moment";
    }
}
