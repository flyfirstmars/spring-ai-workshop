package ge.jar.springaiworkshop.voyagermate.tools;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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

    @Tool(name = "calendar_validator", description = "Validate dates and provide calendar information for trip planning accuracy")
    public String validateCalendar(
            @ToolParam(description = "Date to validate (YYYY-MM-DD format)") String dateString,
            @ToolParam(description = "Optional: timezone for local time context (e.g., Europe/Rome, Asia/Tokyo)") String timezone
    ) {
        try {
            LocalDate date = LocalDate.parse(dateString);
            LocalDate today = LocalDate.now();

            StringBuilder result = new StringBuilder();

            if (date.isBefore(today)) {
                result.append("WARNING: Date ").append(dateString).append(" is in the past. ");
            } else if (date.isEqual(today)) {
                result.append("Date ").append(dateString).append(" is today. ");
            } else {
                long daysFromNow = ChronoUnit.DAYS.between(today, date);
                result.append("Date ").append(dateString).append(" is ").append(daysFromNow).append(" days from now. ");
            }

            DayOfWeek dayOfWeek = date.getDayOfWeek();
            result.append("It falls on a ").append(dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(". ");

            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                result.append("This is a weekend day. ");
            } else {
                result.append("This is a weekday. ");
            }

            Month month = date.getMonth();
            result.append("Month: ").append(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(" (");

            switch (month) {
                case DECEMBER, JANUARY, FEBRUARY -> result.append("Winter");
                case MARCH, APRIL, MAY -> result.append("Spring");
                case JUNE, JULY, AUGUST -> result.append("Summer");
                case SEPTEMBER, OCTOBER, NOVEMBER -> result.append("Autumn");
            }
            result.append(" in Northern Hemisphere). ");

            if (timezone != null && !timezone.trim().isEmpty()) {
                try {
                    ZoneId zoneId = ZoneId.of(timezone);
                    ZonedDateTime nowInZone = ZonedDateTime.now(zoneId);

                    result.append("Local timezone: ").append(zoneId.getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(". ");
                    result.append("Current local time: ").append(nowInZone.format(DateTimeFormatter.ofPattern("HH:mm 'on' EEEE, MMMM d, yyyy"))).append(". ");
                } catch (Exception e) {
                    result.append("Note: Invalid timezone '").append(timezone).append("' - using system default. ");
                }
            }

            return result.toString().trim();

        } catch (Exception e) {
            return "ERROR: Invalid date format '" + dateString + "'. Please use YYYY-MM-DD format (e.g., 2024-12-01).";
        }
    }
}
