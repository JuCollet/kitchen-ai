package dev.julco.kitchen.pantry;

import java.util.List;

/**
 * The pantry in short, for get_context: names and quantities, at most {@code PantryService.SUMMARY_SIZE}
 * of them (most recently updated first); {@code itemCount} tells whether get_pantry has more.
 */
public record PantrySummary(long itemCount, List<Line> items) {

    public record Line(String name, String quantity) {}
}
