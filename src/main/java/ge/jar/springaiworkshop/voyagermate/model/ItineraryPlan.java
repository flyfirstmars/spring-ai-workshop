package ge.jar.springaiworkshop.voyagermate.model;

import java.util.List;

public record ItineraryPlan(
        String destinationOverview,
        List<String> highlights,
        List<ItineraryDay> dailySchedule,
        List<String> bookingReminders,
        double estimatedBudget
) {
}
