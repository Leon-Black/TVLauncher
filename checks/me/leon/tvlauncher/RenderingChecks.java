package me.leon.tvlauncher;

/** Host checks for focus scrolling with content padding and focus alignment. */
public final class RenderingChecks {
    private static int checks;

    public static void main(String[] args) {
        int viewport = 1920, inset = 80, content = 5500;
        require(ScrollBounds.target(0, 80, 490, viewport, inset, inset, content) == 0,
                "First Dock card must not consume the left gutter");
        int moved = ScrollBounds.target(0, 1880, 2290, viewport, inset, inset, content);
        require(moved == 450, "Next offscreen card must align within the right gutter");
        require(2290 - moved == viewport - inset, "Right inset must remain after scrolling");
        require(ScrollBounds.target(moved, 80, 490, viewport, inset, inset, content) == 0,
                "Returning to the first card must restore the left inset");
        int end = ScrollBounds.target(0, 5170, 5580, viewport, inset, inset, content);
        require(end == 3740 && 5580 - end == viewport - inset, "Last card must keep the right inset");
        require(ScrollBounds.target(0, 80, 490, viewport, inset, inset, 410) == 0,
                "Short content must never scroll negatively");
        int bottom = ScrollBounds.target(0, 808, 1128, 1080, 0, 40, 1128);
        require(bottom == 88 && 1128 - bottom == 1080 - 40,
                "The scrolling title must preserve the list's 40px bottom gutter");
        require(ScrollBounds.target(bottom, 155, 451, 1080, 0, 40, 1128, true) == 0,
                "Returning from below to the first row must restore the entire heading and gap");
        require(ScrollBounds.target(28, 155, 451, 1080, 0, 40, 1128, true) == 0,
                "Restoring a first-row focus must clear the old residual scroll offset");
        require(ScrollBounds.target(0, 155, 451, 1080, 0, 40, 1128, true) == 0,
                "First-row focus must preserve an already visible heading");
        require(ScrollBounds.target(600, 500, 793, 1080, 0, 40, 1800) == 500,
                "Other rows must reveal upward without reserving a fixed header");
        require(ScrollBounds.target(450, 500, 793, 1080, 0, 40, 1800) == 450,
                "Visible later rows must retain their scroll position");
        require(ScrollBounds.target(350, 1430, 1840, viewport, inset, inset, content) == 350,
                "A fully visible card must retain the current position");
        require(ScrollBounds.target(3000, 530, 940, viewport, inset, inset, content) == 450,
                "A rapid reverse movement must reveal the new target from the current position");
        System.out.println("PASS: " + checks + " scroll boundary checks");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
