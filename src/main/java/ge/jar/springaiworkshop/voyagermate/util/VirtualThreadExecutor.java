package ge.jar.springaiworkshop.voyagermate.util;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Utility class for executing tasks on virtual threads with proper exception handling.
 */
public final class VirtualThreadExecutor {

    private VirtualThreadExecutor() {
    }

    /**
     * Executes a task on a virtual thread and waits for the result.
     *
     * @param task           the task to execute
     * @param failureMessage message to include in exceptions if the task fails
     * @param <T>            the return type
     * @return the result of the task
     * @throws IllegalStateException if the task fails or is interrupted
     */
    public static <T> T execute(Callable<T> task, String failureMessage) {
        var future = new FutureTask<>(task);
        Thread.ofVirtual().start(future);
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage + " (interrupted)", ex);
        } catch (ExecutionException ex) {
            var cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new IllegalStateException(failureMessage, cause);
        }
    }

    /**
     * Returns the value if non-null and non-blank, otherwise returns the fallback.
     */
    public static String defaultValue(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Formats a date for display, returning "unscheduled" if null.
     */
    public static String formatDate(LocalDate date) {
        return date == null ? "unscheduled" : date.toString();
    }
}

