package ge.jar.springaiworkshop.voyagermate.model;

import java.time.LocalDate;
import java.util.List;

public record TripPlanRequest(
        String travellerName,
        String originCity,
        String destinationCity,
        LocalDate departureDate,
        LocalDate returnDate,
        String budgetFocus,
        List<String> interests
) {
}
