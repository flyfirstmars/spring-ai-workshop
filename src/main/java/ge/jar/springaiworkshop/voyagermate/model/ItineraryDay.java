package ge.jar.springaiworkshop.voyagermate.model;

import java.util.List;

public record ItineraryDay(
    String day,
    String theme,
    List<String> activities,
    String diningRecommendation
) {
}
