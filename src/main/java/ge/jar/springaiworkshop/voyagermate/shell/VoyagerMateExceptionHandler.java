package ge.jar.springaiworkshop.voyagermate.shell;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;

@ShellComponent
public final class VoyagerMateExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(VoyagerMateExceptionHandler.class);

    private VoyagerMateExceptionHandler() {
    }

    public static String handleGenericError(Exception ex) {
        Objects.requireNonNull(ex, "Exception must not be null");
        logger.error("Unexpected error", ex);
        var error = VoyagerMateError.fromException(ex);
        return format(error);
    }

    private static String format(VoyagerMateError error) {
        return switch (error) {
            case VoyagerMateError.NetworkError(var endpoint, var cause) ->
                    compose(error, "Endpoint: %s, Cause: %s".formatted(valueOrUnknown(endpoint), valueOrUnknown(cause)));
            case VoyagerMateError.RateLimitError(var limitType, var retryAfter) ->
                    compose(error, "Limit: %s, Retry after: %s".formatted(valueOrUnknown(limitType), valueOrUnknown(retryAfter)));
            case VoyagerMateError.BadRequestError(var parameter, var value, var reason) ->
                    compose(error, "Parameter: %s, Value: %s, Reason: %s".formatted(
                            valueOrUnknown(parameter), valueOrUnknown(value), valueOrUnknown(reason)));
            case VoyagerMateError.TimeoutError(var operation, var timeoutMs) ->
                    compose(error, "Operation: %s, Timeout: %d ms".formatted(valueOrUnknown(operation), timeoutMs));
            case VoyagerMateError.QuotaExceededError(var quotaType, var currentUsage, var limit) ->
                    compose(error, "Quota: %s, Usage: %s, Limit: %s".formatted(
                            valueOrUnknown(quotaType), valueOrUnknown(currentUsage), valueOrUnknown(limit)));
            case VoyagerMateError.ContentFilterError(var filterType, var triggeredContent) ->
                    compose(error, "Filter: %s, Input: %s".formatted(valueOrUnknown(filterType), valueOrUnknown(triggeredContent)));
            case VoyagerMateError.FileError(var operation, var filePath, var reason) ->
                    compose(error, "Operation: %s, Path: %s, Reason: %s".formatted(
                            valueOrUnknown(operation), valueOrUnknown(filePath), valueOrUnknown(reason)));
            case VoyagerMateError.ValidationError(var field, var value, var constraint) ->
                    compose(error, "Field: %s, Value: %s, Constraint: %s".formatted(
                            valueOrUnknown(field), valueOrUnknown(value), valueOrUnknown(constraint)));
            case VoyagerMateError.UnknownError(var originalMessage, var exceptionType) ->
                    compose(error, "Type: %s, Message: %s".formatted(
                            valueOrUnknown(exceptionType), valueOrUnknown(originalMessage)));
            default -> compose(error, error.getTechnicalDetails());
        };
    }

    private static String compose(VoyagerMateError error, String details) {
        return "%s%nDetails: %s%nGuidance: %s".formatted(
                valueOrFallback(error.getUserMessage(), "Unhandled error"),
                valueOrFallback(details, "Not specified"),
                valueOrFallback(error.getSuggestedActions(), "Not specified")
        );
    }

    private static String valueOrFallback(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String valueOrUnknown(Object value) {
        if (value == null) {
            return "unknown";
        }
        var text = value.toString();
        return text.isBlank() ? "unknown" : text;
    }
}
