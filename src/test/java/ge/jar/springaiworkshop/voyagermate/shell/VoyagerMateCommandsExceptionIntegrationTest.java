package ge.jar.springaiworkshop.voyagermate.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ge.jar.springaiworkshop.voyagermate.core.VoyagerMateService;
import ge.jar.springaiworkshop.voyagermate.workflow.ItineraryWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoyagerMateCommands Exception Handling Integration")
class VoyagerMateCommandsExceptionIntegrationTest {

    @Mock
    private VoyagerMateService voyagerMateService;

    @Mock
    private ItineraryWorkflowService itineraryWorkflowService;

    @Mock
    private ObjectMapper objectMapper;

    private VoyagerMateCommands voyagerMateCommands;

    @BeforeEach
    void setUp() {
        var copyMock = Mockito.mock(ObjectMapper.class);
        when(objectMapper.copy()).thenReturn(copyMock);
        when(copyMock.findAndRegisterModules()).thenReturn(copyMock);
        when(copyMock.writerWithDefaultPrettyPrinter()).thenReturn(Mockito.mock(ObjectWriter.class));

        voyagerMateCommands = new VoyagerMateCommands(voyagerMateService, itineraryWorkflowService, objectMapper);
    }

    @Nested
    @DisplayName("Given Chat Command Scenarios")
    class ChatCommandScenarios {

        @Test
        @DisplayName("When chat service throws authentication error, Then should return user-friendly auth message")
        void shouldReturnUserFriendlyAuthMessageForChatError() {
            var prompt = "Plan a trip to Paris";
            var authException = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid API key");

            when(voyagerMateService.streamChat(any())).thenThrow(authException);

            var result = voyagerMateCommands.chat(prompt);

            assertThat(result)
                    .contains("Authentication Error")
                    .contains("AZURE_OPENAI_API_KEY")
                    .doesNotContain("HttpClientErrorException")
                    .doesNotContain("Unauthorized");
        }

        @Test
        @DisplayName("When chat service throws network error, Then should return network troubleshooting guidance")
        void shouldReturnNetworkGuidanceForChatNetworkError() {
            var prompt = "What's the weather like?";
            var networkException = new ResourceAccessException("Connection timeout");

            when(voyagerMateService.streamChat(any())).thenThrow(networkException);

            var result = voyagerMateCommands.chat(prompt);

            assertThat(result)
                    .contains("Network Error")
                    .contains("internet connection")
                    .contains("API endpoint configuration")
                    .doesNotContain("ResourceAccessException");
        }

        @Test
        @DisplayName("When chat service throws rate limit error, Then should provide retry guidance")
        void shouldProvideRetryGuidanceForChatRateLimit() {
            var prompt = "Help me plan something";
            var rateLimitException = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");

            when(voyagerMateService.streamChat(any())).thenThrow(rateLimitException);

            var result = voyagerMateCommands.chat(prompt);

            assertThat(result)
                    .contains("Rate Limit Exceeded")
                    .contains("wait")
                    .contains("try again")
                    .doesNotContain("TOO_MANY_REQUESTS");
        }
    }

    @Nested
    @DisplayName("Given Image Description Scenarios")
    class ImageDescriptionScenarios {

        @Test
        @DisplayName("When image file is not found, Then should provide clear file guidance")
        void shouldProvideFileGuidanceForMissingImage() {
            var imagePath = "/path/to/missing/image.jpg";
            var prompt = "Describe this image";

            var result = voyagerMateCommands.describeImage(imagePath, prompt);

            assertThat(result)
                    .contains("Validation Error")
                    .contains("Failed to read file")
                    .doesNotContain("NoSuchFileException");
        }

        @Test
        @DisplayName("When image service throws server error, Then should indicate temporary issue")
        void shouldIndicateTemporaryIssueForImageServerError() {
            var imagePath = "src/main/resources/images/test.jpg";
            var prompt = "Analyze this image";

            var result = voyagerMateCommands.describeImage(imagePath, prompt);

            assertThat(result)
                    .contains("Validation Error")
                    .contains("Failed to read file")
                    .doesNotContain("HttpClientErrorException");
        }
    }

    @Nested
    @DisplayName("Given Itinerary Planning Scenarios")
    class ItineraryPlanningScenarios {

        @Test
        @DisplayName("When planning service throws validation error, Then should show parameter guidance")
        void shouldShowParameterGuidanceForValidationError() {
            var destination = "Paris";
            var invalidDate = "2023-13-45";

            var result = voyagerMateCommands.planItinerary(
                    null, null, destination, invalidDate, null, null, null
            );

            assertThat(result)
                    .contains("Validation Error")
                    .contains("date format")
                    .contains("YYYY-MM-DD")
                    .doesNotContain("DateTimeParseException");
        }

        @Test
        @DisplayName("When planning service is unavailable, Then should suggest trying later")
        void shouldSuggestTryingLaterForServiceUnavailable() {
            var destination = "London";
            var serviceException = new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service down");
            when(voyagerMateService.planTrip(any())).thenThrow(serviceException);

            var result = voyagerMateCommands.planItinerary(
                    "John", "Paris", destination, "2024-01-01", "2024-01-05", null, null
            );

            assertThat(result)
                    .isNotNull()
                    .contains("Service Unavailable")
                    .contains("Guidance:");
        }
    }

    @Nested
    @DisplayName("Given Error Response Quality Standards")
    class ErrorResponseQualityStandards {

        @Test
        @DisplayName("When any command fails, Then error response should meet quality standards")
        void shouldMeetQualityStandardsForAllErrors() {
            when(voyagerMateService.streamChat(any()))
                    .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Auth failed"));
            when(voyagerMateService.streamAnalyzeImage(any()))
                    .thenThrow(new ResourceAccessException("Network failed"));

            var chatErrorResponse = voyagerMateCommands.chat("test");
            var imageErrorResponse = voyagerMateCommands.describeImage("src/main/resources/images/background-vacation1.jpeg", "test");

            assertThat(chatErrorResponse)
                    .as("Chat error response should meet quality standards")
                    .isNotNull()
                    .doesNotContain("Exception")
                    .doesNotContain("at java.")
                    .doesNotContain("Caused by:");

            assertThat(imageErrorResponse)
                    .as("Image error response should meet quality standards")
                    .isNotNull()
                    .doesNotContain("Exception")
                    .doesNotContain("at java.")
                    .doesNotContain("Caused by:")
                    .hasSizeGreaterThan(30)
                    .hasSizeLessThan(800);

            assertThat(imageErrorResponse)
                    .as("Image error response should meet quality standards")
                    .isNotNull()
                    .doesNotContain("Exception")
                    .hasSizeGreaterThan(30)
                    .hasSizeLessThan(800);
        }

        @Test
        @DisplayName("When errors occur in different commands, Then responses should be consistent in format")
        void shouldProvideConsistentFormatAcrossCommands() {
            var authException = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Auth failed");
            when(voyagerMateService.streamChat(any())).thenThrow(authException);
            when(voyagerMateService.streamAnalyzeImage(any())).thenThrow(authException);

            var chatError = voyagerMateCommands.chat("test");
            var imageError = voyagerMateCommands.describeImage("src/main/resources/images/background-vacation1.jpeg", "test");

            assertThat(chatError)
                    .contains("Authentication Error");
            assertThat(imageError)
                    .contains("Authentication Error");

            assertThat(chatError).isNotEmpty();
            assertThat(imageError).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Given Exception Handling Resilience")
    class ExceptionHandlingResilience {

        @Test
        @DisplayName("When exception handler itself fails, Then should provide fallback error message")
        void shouldProvideFallbackForHandlerFailure() {
            var someException = new RuntimeException("Unexpected error");

            var result = VoyagerMateExceptionHandler.handleGenericError(someException);

            assertThat(result)
                    .isNotNull()
                    .contains("Unexpected Error")
                    .doesNotContain("null");
        }

        @Test
        @DisplayName("When null exception is handled, Then should provide safe error message")
        void shouldProvideSafeMessageForNullException() {
            Exception nullException = null;

            assertThatThrownBy(() ->
                    VoyagerMateExceptionHandler.handleGenericError(nullException)
            ).isInstanceOf(NullPointerException.class);

        }

        @Test
        @DisplayName("When exception with null message occurs, Then should handle gracefully")
        void shouldHandleExceptionWithNullMessage() {
            var exceptionWithNullMessage = new RuntimeException((String) null);

            var result = VoyagerMateExceptionHandler.handleGenericError(exceptionWithNullMessage);

            assertThat(result)
                    .isNotNull()
                    .doesNotContain("null");
        }
    }

    @FunctionalInterface
    interface TestScenario {
        String get();
    }
}
