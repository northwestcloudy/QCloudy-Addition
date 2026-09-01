package cloudy.autume.addition.profile.market;

import java.util.List;

/** Pixel-column metrics for rendering price rows without inserting spaces. */
public record PriceTooltipLayout(List<PriceTooltipRow> rows,
                                 int labelWidth,
                                 int valueColumnX,
                                 int totalWidth) {
    public PriceTooltipLayout {
        rows = List.copyOf(rows);
        if (labelWidth < 0 || valueColumnX < 0 || totalWidth < 0) {
            throw new IllegalArgumentException("Negative tooltip layout metric");
        }
        if (!rows.isEmpty() && valueColumnX < labelWidth) {
            throw new IllegalArgumentException("Value column overlaps labels");
        }
    }
}
