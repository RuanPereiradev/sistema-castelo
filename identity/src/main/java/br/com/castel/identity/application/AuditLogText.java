package br.com.castel.identity.application;

/**
 * Turns text typed by a client into a value safe to write into one line of the audit log.
 *
 * <p>The login audit log records the username exactly as tried, including usernames that are not
 * well formed, and those are arbitrary client text: they may carry line breaks that would forge a
 * log line, control or invisible characters that would hide part of it, quotes that would fake a
 * {@code key=value} field, or megabytes of padding. So the value is:
 *
 * <ul>
 *   <li><b>quoted</b>, with {@code "} and {@code \} escaped, so the value can never end early and
 *       a following {@code , ip=...} inside it is plainly part of the value;
 *   <li><b>escaped</b>: CR, LF and TAB as {@code \r}, {@code \n}, {@code \t}; every other ISO
 *       control, format (bidirectional overrides, zero-width), line separator, paragraph separator
 *       and lone surrogate code point as {@code \}{@code uXXXX}. Printable Unicode such as
 *       {@code İ} stays readable, because telling {@code cozİnha} from {@code cozinha} is exactly
 *       what an investigation needs;
 *   <li><b>truncated</b> to {@value #MAXIMUM_CODE_POINTS} code points, with a marker outside the
 *       quotes carrying the original length. A real username has at most 30 characters, so a
 *       username that exists is never truncated.
 * </ul>
 */
final class AuditLogText {

    static final int MAXIMUM_CODE_POINTS = 64;

    private AuditLogText() {
    }

    /** The quoted, escaped and truncated form of {@code clientText}; {@code null} becomes {@code <null>}. */
    static String quote(String clientText) {
        if (clientText == null) {
            return "<null>";
        }
        StringBuilder quoted = new StringBuilder(Math.min(clientText.length(), MAXIMUM_CODE_POINTS) + 2).append('"');
        int index = 0;
        int codePoints = 0;
        while (index < clientText.length() && codePoints < MAXIMUM_CODE_POINTS) {
            int codePoint = clientText.codePointAt(index);
            appendEscaped(quoted, codePoint);
            index += Character.charCount(codePoint);
            codePoints++;
        }
        quoted.append('"');
        if (index < clientText.length()) {
            quoted.append("[truncated, ").append(clientText.length()).append(" chars]");
        }
        return quoted.toString();
    }

    private static void appendEscaped(StringBuilder target, int codePoint) {
        switch (codePoint) {
            case '"' -> target.append("\\\"");
            case '\\' -> target.append("\\\\");
            case '\n' -> target.append("\\n");
            case '\r' -> target.append("\\r");
            case '\t' -> target.append("\\t");
            default -> {
                if (isHiddenOrLineBreaking(codePoint)) {
                    target.append(codePoint > 0xFFFF ? "\\u{%X}".formatted(codePoint) : "\\u%04X".formatted(codePoint));
                } else {
                    target.appendCodePoint(codePoint);
                }
            }
        }
    }

    private static boolean isHiddenOrLineBreaking(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SURROGATE;
    }
}
