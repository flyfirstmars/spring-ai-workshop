package ge.jar.springaiworkshop.voyagermate.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class VoyagerTools {

    private static final Map<String, List<String>> ATTRACTIONS = Map.of(
        "rome", List.of("Colosseum tour", "Sunset walk at Trastevere", "Day trip to Pompeii"),
        "tokyo", List.of("Tsukiji outer market tasting", "Ghibli Museum", "Mount Takao hike"),
        "barcelona", List.of("Sagrada Família early access", "Tapas crawl in El Born", "Costa Brava sail")
    );

    private static final Map<String, Double> AVERAGE_DAILY_BUDGET = Map.of(
        "rome", 185.0,
        "tokyo", 220.0,
        "barcelona", 170.0,
        "bali", 110.0
    );

    @Tool(name = "find_attractions", description = "Return must-see experiences for a destination")
    public List<String> findAttractions(
        @ToolParam(description = "Destination city") String city,
        @ToolParam(description = "Maximum number of items") Integer limit
    ) {
        var list = ATTRACTIONS.getOrDefault(city.toLowerCase(), List.of("Curate experiences locally upon arrival"));
        if (limit == null || limit >= list.size()) {
            return list;
        }
        return list.subList(0, Math.max(limit, 0));
    }

    @Tool(name = "estimate_budget", description = "Estimate base budget per traveller for a trip")
    public double estimateBudget(
        @ToolParam(description = "Destination city") String city,
        @ToolParam(description = "Number of nights") int nights
    ) {
        var baseline = AVERAGE_DAILY_BUDGET.getOrDefault(city.toLowerCase(), 150.0);
        return nights * baseline;
    }

    @Tool(name = "travel_gap_checker", description = "Suggest buffer days between travel legs")
    public String travelGapChecker(
        @ToolParam(description = "Date you leave origin") LocalDate start,
        @ToolParam(description = "Date you depart destination") LocalDate end
    ) {
        long nights = ChronoUnit.DAYS.between(start, end);
        if (nights < 3) {
            return "Trip is very short. Add at least 1 buffer night to adjust to time zone changes.";
        }
        if (nights > 14) {
            return "Consider planning a rest day every 4 days to avoid burnout.";
        }
        return "Duration looks balanced. Add a flex day for unexpected discoveries.";
    }
}
