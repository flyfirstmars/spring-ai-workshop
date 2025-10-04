# VoyagerMate Technical Reference

This document consolidates technical implementation details, exception handling strategies, and advanced features for
the VoyagerMate Spring AI workshop project.

## Table of Contents

1. [Exception Handling](#exception-handling)
2. [Tool Call Visibility](#tool-call-visibility)
3. [Modern Java Features](#modern-java-features)

## Exception Handling

### Spring Shell vs Web Applications

Spring Shell applications require different exception handling approaches compared to web applications:

| Web Apps (@ControllerAdvice) | Spring Shell           |
|------------------------------|------------------------|
| Global exception handling    | Method-level try-catch |
| HTTP status codes            | User-friendly messages |
| JSON error responses         | Console text output    |
| Request/Response model       | Command/Result model   |

### Implementation Strategies

#### 1. Try-Catch in @ShellMethod (Recommended)

```java

@ShellMethod(key = "chat", value = "Chat with VoyagerMate")
public String chat(@ShellOption String prompt) {
    try {
        return voyagerMateService.processChat(prompt);
    } catch (Exception ex) {
        return handleException(ex);
    }
}
```

#### 2. Centralized Exception Handler

```java

@ShellComponent
public final class VoyagerMateExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(VoyagerMateExceptionHandler.class);

    private VoyagerMateExceptionHandler() {
    }

    public static String handleGenericError(Exception ex) {
        logger.error("Unexpected error", ex);
        var error = VoyagerMateError.fromException(ex);
        return switch (error) {
            case VoyagerMateError.NetworkError(var endpoint, var cause) ->
                    format(error, "Endpoint: %s, Cause: %s".formatted(endpoint, cause));
            case VoyagerMateError.RateLimitError(var limitType, var retryAfter) ->
                    format(error, "Limit: %s, Retry after: %s".formatted(limitType, retryAfter));
            default -> format(error, error.getTechnicalDetails());
        };
    }

    private static String format(VoyagerMateError error, String details) {
        return "%s\nDetails: %s\nGuidance: %s".formatted(
                error.getUserMessage(),
                details,
                error.getSuggestedActions()
        );
    }
}
```

### Azure OpenAI Exception Types

#### HTTP Status Code Exceptions

**4xx Client Errors:**

| Status Code | Exception Type       | Description            | Common Causes                            |
|-------------|----------------------|------------------------|------------------------------------------|
| 400         | Bad Request          | Invalid request format | Malformed JSON, invalid parameters       |
| 401         | Authentication Error | Invalid API key        | Wrong AZURE_OPENAI_API_KEY               |
| 403         | Permission Denied    | Access forbidden       | Insufficient permissions, quota exceeded |
| 404         | Not Found Error      | Resource not found     | Wrong endpoint, invalid deployment       |
| 422         | Unprocessable Entity | Semantically incorrect | Content policy violations                |
| 429         | Rate Limit Error     | Too many requests      | Exceeding requests per minute            |

**5xx Server Errors:**

| Status Code | Exception Type        | Description                | Recovery Action                |
|-------------|-----------------------|----------------------------|--------------------------------|
| 500         | Internal Server Error | Azure OpenAI service issue | Retry with exponential backoff |
| 502         | Bad Gateway           | Gateway issue              | Retry after delay              |
| 503         | Service Unavailable   | Service temporarily down   | Wait and retry                 |
| 504         | Gateway Timeout       | Request timeout            | Reduce request complexity      |

#### Spring AI Framework Exceptions

```java
// General AI exceptions
org.springframework.ai.retry.TransientAiException      // Temporary failures
org.springframework.ai.retry.NonTransientAiException  // Permanent failures

// Chat Client exceptions
org.springframework.ai.chat.client.ChatClientException // Chat-specific errors

// Azure-specific exceptions (wrapped)
org.springframework.web.client.HttpClientErrorException      // 4xx errors
org.springframework.web.client.HttpServerErrorException      // 5xx errors
org.springframework.web.client.ResourceAccessException       // Network/connectivity
org.springframework.web.client.RestClientException           // General REST errors
```

#### Network and Connectivity Exceptions

```java
java.net.ConnectException                    // Connection refused
java.net.SocketTimeoutException             // Request timeout
java.net.UnknownHostException               // DNS resolution failure
org.springframework.web.client.ResourceAccessException  // Network access issues
```

### Exception Handling Patterns

#### By Operation Type

**Chat Operations:**

```java
try{
ChatResponse response = chatClient.prompt().user(prompt).call();
}catch(
HttpClientErrorException ex){
        // Handle 4xx errors (auth, permissions, rate limits)
        }catch(
HttpServerErrorException ex){
        // Handle 5xx errors (service issues)
        }catch(
ResourceAccessException ex){
        // Handle network connectivity issues
        }catch(
RestClientException ex){
        // Handle other REST-related issues
        }
```

**Audio Transcription:**

```java
try{
String transcript = audioTranscriptionModel.call(audioResource);
}catch(
HttpClientErrorException ex){
        // Common: 400 (unsupported format), 429 (rate limit)
        }catch(
IllegalArgumentException ex){
        // Invalid audio format or empty file
        }
```

**Image Analysis:**

```java
try{
ChatResponse response = chatClient.prompt().messages(userMessage).call();
}catch(
HttpClientErrorException ex){
        // Common: 400 (invalid image), 413 (image too large)
        }catch(
HttpServerErrorException ex){
        // 500 (vision processing errors)
        }
```

### Content Safety Filtering

Azure OpenAI includes content safety filters:

```java
{
        "error":{
        "code":"content_filter",
        "message":"The response was filtered due to content policy violations"
        }
        }
```

**Detection patterns:**

- Message contains: "content filter", "safety", "policy violation"
- HTTP 400 with content filter error code
- Response with finish_reason: "content_filter"

### Retry Configuration

```yaml
spring:
  ai:
    retry:
      max-attempts: 3
      backoff:
        initial-interval: 1s
        multiplier: 2
        max-interval: 10s
```

### User-Friendly Error Messages

Map Azure error codes to actionable user messages:

```java
400->"Invalid request format - check your input"
        401->"Authentication failed - verify your API key"
        403->"Access denied - check permissions and quotas"
        404->"Service not found - verify endpoint configuration"
        429->"Rate limit exceeded - please wait and try again"
        500->"Service temporarily unavailable - please retry"
```

### Best Practices

1. **Consistent Error Format**
    - Start with concise label (e.g., "Authentication Error")
    - Explain what happened
    - Provide actionable remediation

2. **Proper Logging**
   ```java
   logger.error("Azure OpenAI API error: {}", ex.getMessage(), ex);
   return "Service Error: Unable to process request\nPlease try again later";
   ```

3. **Exception Hierarchy**
   ```java
   if (ex instanceof NoSuchFileException) {
       // Handle file not found
   } else if (ex instanceof IOException) {
       // Handle general IO errors
   } else {
       // Handle all other exceptions
   }
   ```

4. **Graceful Degradation**
   ```java
   try {
       return generateFullResponse();
   } catch (Exception ex) {
       logger.warn("Full response failed, using fallback", ex);
       return generateBasicResponse();
   }
   ```

## Tool Call Visibility

The Tool Call feature provides transparency into AI reasoning by displaying which tools were invoked during processing.

### Implementation

#### Data Model

```java
public record ChatResponsePayload(String reply, String model, long latencyMs, List<String> toolCalls) {
    // Backward-compatible constructor
    public ChatResponsePayload(String reply, String model, long latencyMs) {
        this(reply, model, latencyMs, List.of());
    }
}
```

#### Tool Call Extraction

```java
private List<String> extractToolCalls(ChatResponse chatResponse) {
    if (chatResponse == null || chatResponse.getResult() == null) {
        return List.of();
    }

    var result = chatResponse.getResult();
    var toolCalls = result.getOutput().getToolCalls();

    if (toolCalls == null || toolCalls.isEmpty()) {
        return List.of();
    }

    return toolCalls.stream()
            .map(toolCall -> toolCall.name())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

#### Display Format

```java
private String formatChatResponse(ChatResponsePayload payload, boolean includeReply) {
    var builder = new StringBuilder()
            .append("Model: ")
            .append(payload.model())
            .append(System.lineSeparator());

    if (!payload.toolCalls().isEmpty()) {
        builder.append("ToolCall: ")
                .append(String.join(", ", payload.toolCalls()))
                .append(System.lineSeparator());
    }

    builder.append("Latency: ")
            .append(payload.latencyMs())
            .append(" ms");
}
```

### Available Tools

**find_attractions**

- Purpose: Returns must-see experiences for a destination
- Parameters: city (String), limit (Integer)
- Supported Cities: Rome, Tokyo, Barcelona

**estimate_budget**

- Purpose: Estimates base budget per traveler
- Parameters: city (String), nights (int)
- Daily rates: Rome (185), Tokyo (220), Barcelona (170), Bali (110)

**travel_gap_checker**

- Purpose: Suggests buffer days between travel legs
- Parameters: start (LocalDate), end (LocalDate)
- Logic: Recommends rest days based on trip duration

### Output Examples

**Command without tools:**

```
Model: azure-openai
Latency: 711 ms
```

**Command with tools:**

```
Model: azure-openai
ToolCall: find_attractions, estimate_budget
Latency: 1850 ms
```

### Streaming Support

Tool calls are collected and deduplicated across streaming responses:

```java
var toolCalls = responses.stream()
        .map(ChatClientResponse::chatResponse)
        .filter(Objects::nonNull)
        .flatMap(response -> extractToolCalls(response).stream())
        .distinct()
        .collect(Collectors.toList());
```

### Debug Logging

Enable debug logging to see tool execution:

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

Output:

```
DEBUG o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: estimate_budget
DEBUG o.s.ai.tool.method.MethodToolCallback    : Starting execution of tool: estimate_budget
DEBUG o.s.ai.tool.method.MethodToolCallback    : Successful execution of tool: estimate_budget
```

## Modern Java Features

### Sealed Classes for Exception Handling

Java 21+ features enable type-safe, exhaustive exception handling.

#### Traditional Approach (Problematic)

```java
// Old style: instanceof checks with static utility methods
public static String handleException(Exception ex) {
    if (ex instanceof ResourceAccessException) {
        return handleNetworkError((ResourceAccessException) ex);
    } else if (ex instanceof HttpClientErrorException) {
        return handleHttpClientError(ex);
    }
    // ... many more instanceof checks
    return handleGenericError(ex);
}
```

**Problems:**

- Not type-safe
- Not exhaustive (no compile-time guarantee)
- Hard to maintain
- Verbose with repetitive instanceof checks
- Error-prone when adding new exception types

#### Modern Approach (Recommended)

```java
// Modern style: sealed classes with pattern matching
public static String handleException(Exception ex) {
    var error = VoyagerMateError.fromException(ex);
    return switch (error) {
        case VoyagerMateError.NetworkError networkError -> formatNetworkError(networkError);
        case VoyagerMateError.AuthenticationError authError -> formatAuthError(authError);
        case VoyagerMateError.RateLimitError rateLimitError -> formatRateLimitError(rateLimitError);
        // Compiler ensures ALL cases are handled
    };
}
```

**Benefits:**

- Type-safe: All error types known at compile time
- Exhaustive: Compiler enforces handling of all variants
- Maintainable: Adding new types forces updates everywhere
- Concise: Pattern matching eliminates instanceof checks
- Data-rich: Each error type carries specific information

### Java 21+ Features Leveraged

#### 1. Sealed Classes (Java 17+)

```java
public sealed interface VoyagerMateError
        permits VoyagerMateError.NetworkError,
        VoyagerMateError.AuthenticationError,
        VoyagerMateError.RateLimitError
```

Benefits:

- Compile-time guarantee of all possible subtypes
- No unknown implementations
- Perfect for finite set of error conditions

#### 2. Records (Java 14+)

```java
record NetworkError(String endpoint, String cause) implements VoyagerMateError {
    @Override
    public String getUserMessage() {
        return "Network Error: Unable to connect to Azure OpenAI service";
    }
}
```

Benefits:

- Immutable data carriers
- Automatic equals(), hashCode(), toString()
- Clean, concise syntax

#### 3. Pattern Matching in Switch (Java 17+, Enhanced in 21)

```java
return switch(error){
        case
VoyagerMateError.NetworkError networkError ->{
        logger.

error("Network issue: {}",networkError.endpoint());

yield formatNetworkError(networkError);
    }
            case
VoyagerMateError.RateLimitError rateLimitError ->{
var delay = parseRetryDelay(rateLimitError.retryAfter());

yield formatRateLimitError(rateLimitError, delay);
    }
            };
```

Benefits:

- Exhaustive checking
- Type-safe field access without casting
- Clean, readable structure
- yield keyword for complex logic

#### 4. Enhanced instanceof (Java 16+)

```java
static VoyagerMateError fromException(Exception ex) {
    return switch (ex) {
        case HttpClientErrorException hcee when hcee.getStatusCode().value() == 401 ->
                new AuthenticationError(hcee.getMessage());

        case ResourceAccessException rae -> new NetworkError(
                extractEndpoint(rae.getMessage()),
                rae.getCause() != null ? rae.getCause().getMessage() : rae.getMessage()
        );

        default -> new UnknownError(ex.getMessage(), ex.getClass().getSimpleName());
    };
}
```

Benefits:

- Pattern matching with guard conditions (when clauses)
- Automatic type narrowing
- Clean exception classification

### Compile-Time Safety

```java
// If you add a new error type to the sealed interface:
record NewErrorType(String details) implements VoyagerMateError { ...
}

// Compiler forces updates to ALL switch expressions:
// Error: The switch expression does not cover all possible input values
return switch(error){
        case
NetworkError networkError ->

handleNetwork(networkError);
    case
AuthenticationError authError ->

handleAuth(authError);
// Missing: case NewErrorType newError -> ...
// COMPILATION FAILS until you add the missing case
};
```

### Rich Error Context

```java
// Traditional - limited context
catch(Exception ex){
        return"Network error occurred";
        }

// Modern - rich, structured context
var networkError = new NetworkError("api.openai.azure.com", "Connection timeout");
return switch(networkError){
        case

NetworkError(var endpoint, var cause) ->"""
         Network Error: Unable to connect to Azure OpenAI service
         Endpoint: %s
         Cause: %s
         Check your internet connection and API endpoint configuration
        """.

formatted(endpoint, cause);
};
```

### Utility Methods with Pattern Matching

```java
public static boolean isRetryable(VoyagerMateError error) {
    return switch (error) {
        case NetworkError ignored -> true;
        case ServerError ignored -> true;
        case RateLimitError ignored -> true;
        case AuthenticationError ignored -> false;
        case ValidationError ignored -> false;
        default -> false;
    };
}

public static long getRetryDelayMs(VoyagerMateError error) {
    return switch (error) {
        case RateLimitError(var limitType, var retryAfter) -> parseRetryDelay(retryAfter);
        case ServerError ignored -> 2000L;
        case NetworkError ignored -> 1000L;
        default -> 0L;
    };
}
```

### Performance Comparison

| Aspect          | Traditional           | Modern Sealed Classes     |
|-----------------|-----------------------|---------------------------|
| Compilation     | Runtime instanceof    | Compile-time type safety  |
| Memory          | Multiple method calls | Single object allocation  |
| Maintainability | Manual updates        | Compiler-enforced updates |
| Type Safety     | Runtime risks         | Compile-time guarantees   |
| Readability     | Verbose if/else       | Clean pattern matching    |
| Extensibility   | Easy to forget        | Impossible to forget      |

### Best Practices

**1. Keep Sealed Hierarchies Focused**

```java
// Good: Focused on specific domain
public sealed interface VoyagerMateError permits NetworkError, AuthError, ValidationError

// Avoid: Too broad or mixing concerns
public sealed interface AppState permits LoadingState, ErrorState, UserData, ConfigData
```

**2. Use Records for Simple Data**

```java
// Good: Simple data with behavior
record NetworkError(String endpoint, String cause) implements VoyagerMateError {
    @Override
    public String getUserMessage() {
        return "Network error";
    }
}

// Avoid: Complex logic in records (use class instead)
```

**3. Leverage Pattern Matching Fully**

```java
// Good: Exhaustive pattern matching
return switch(error){
        case

NetworkError(var endpoint, var cause) ->

handleNetwork(endpoint, cause);
    case

AuthError(var details) ->

handleAuth(details);
    case

ValidationError(var field, var value, var constraint) ->

handleValidation(field, value, constraint);
};

// Avoid: Falling back to instanceof
        if(error instanceof
NetworkError ne){
        return

handleNetwork(ne.endpoint(),ne.

cause());
        }
```

## Testing Strategies

### Exception Testing

```bash
# Test authentication error
export AZURE_OPENAI_API_KEY="invalid-key"
shell:> chat "Hello"

# Test rate limit
for i in {1..10}; do
  shell:> chat "Request $i"
done

# Test network issues
export AZURE_OPENAI_ENDPOINT="https://nonexistent.openai.azure.com"
shell:> chat "Hello"

# Test content filter
shell:> chat "Generate harmful content"
```

### Debug Configuration

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
    org.springframework.web.client: DEBUG
```

## Related Resources

- Spring Shell Reference: https://docs.spring.io/spring-shell/docs/current/reference/htmlsingle/
- Azure OpenAI Service REST API: https://docs.microsoft.com/en-us/azure/cognitive-services/openai/reference
- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- Java Pattern Matching: https://openjdk.org/jeps/441
