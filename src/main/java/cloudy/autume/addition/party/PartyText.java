package cloudy.autume.addition.party;

/** Formatting helpers shared by the pure party parsers. */
final class PartyText {
    private PartyText() {
    }

    static String clean(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\u00a7' && index + 1 < value.length()) {
                index++;
                continue;
            }
            result.append(current == '\r' ? '\n' : current);
        }
        return result.toString().trim();
    }
}
