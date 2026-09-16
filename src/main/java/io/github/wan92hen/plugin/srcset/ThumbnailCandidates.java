package io.github.wan92hen.plugin.srcset;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure helpers deciding whether an image can use Halo's thumbnail endpoint and
 * building the srcset candidates for it.
 *
 * <p>The behaviour is based on measurements against a live Halo 2.26 site:</p>
 * <ul>
 *   <li>{@code /upload/x.webp?width=800} — 2503x1285 / 56 KB becomes
 *       800x410 / 19 KB, so WebP (and PNG/JPEG) resizes fine</li>
 *   <li>{@code /upload/x.gif?width=800} — answers <b>403</b>. A browser does not
 *       fall back to {@code src} when the candidate it picked fails, so emitting
 *       a candidate for a GIF would break the image: GIFs are always skipped</li>
 *   <li>{@code ?height=} is not honoured for PNG and {@code ?size=} is ignored
 *       entirely, so only {@code width} is used</li>
 * </ul>
 *
 * @author wan92hen
 * @since 1.0.0
 */
public final class ThumbnailCandidates {

    /** Extensions whose thumbnails the endpoint refuses, or that need no resizing. */
    public static final Set<String> ALWAYS_SKIP = Set.of("gif", "svg", "ico");

    /**
     * Sources the endpoint re-encodes to JPEG. A JPEG at (nearly) the same pixel
     * size is usually bigger than the WebP it came from, so these formats must be
     * kept to modest widths — see {@link #chooseWidths}.
     */
    public static final Set<String> REENCODED_EXTENSIONS = Set.of("webp");

    /**
     * Widths used for {@link #REENCODED_EXTENSIONS} when the setting is blank.
     * A blank setting must mean "keep the safe default", not "remove the
     * restriction": Halo writes an empty value for a field the user never saw,
     * so an upgrade from a version without this setting would otherwise silently
     * reintroduce the oversized-JPEG regression.
     */
    public static final String DEFAULT_REENCODED_WIDTHS_CSV = "400,800";

    /** Halo serves attachment thumbnails from this path prefix. */
    private static final String UPLOAD_PREFIX = "/upload/";

    /** Query parameters that already pin a size. */
    private static final Set<String> SIZE_PARAMS = Set.of("width", "height", "size");

    private static final int MIN_WIDTH = 16;
    private static final int MAX_WIDTH = 4096;
    private static final int MAX_CANDIDATES = 8;

    private ThumbnailCandidates() {
    }

    /**
     * Parse a comma separated extension list, e.g. {@code ".GIF, svg"} into
     * {@code [gif, svg]}. Blank input yields an empty set.
     */
    public static Set<String> parseExtensions(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.startsWith(".") ? value.substring(1) : value)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Parse a comma separated width list, ignoring unusable entries, keeping the
     * declared order and dropping duplicates.
     */
    public static List<Integer> parseWidths(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        var widths = new LinkedHashSet<Integer>();
        for (var part : csv.split(",")) {
            var value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                var width = Integer.parseInt(value);
                if (width >= MIN_WIDTH && width <= MAX_WIDTH) {
                    widths.add(width);
                }
            } catch (NumberFormatException ignored) {
                // A typo in the settings must not break rendering.
            }
            if (widths.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return List.copyOf(widths);
    }

    /**
     * Pick the candidate widths for one source.
     *
     * <p>Measured on a live 2.26 site: for a WebP source the endpoint answers with
     * JPEG, and at large widths that JPEG is <em>bigger</em> than the WebP it came
     * from (81 KB 1672x940 WebP becomes a 140 KB 1600w JPEG). Retina browsers pick
     * the widest candidate, so a WebP source must only be offered modest widths,
     * otherwise the plugin would make those pages heavier. PNG/JPEG sources keep
     * their own format, so every width below the natural size is a win there.</p>
     *
     * <p>A blank {@code reencodedWidthsCsv} keeps the safe default rather than
     * falling back to the full width list; to give WebP every width, repeat the
     * values in the setting explicitly.</p>
     *
     * @param src the image source
     * @param defaultWidthsCsv widths for sources that keep their format
     * @param reencodedWidthsCsv widths for sources the endpoint re-encodes;
     *        blank or unusable input falls back to
     *        {@link #DEFAULT_REENCODED_WIDTHS_CSV}
     * @return the widths to emit, or an empty list when there are none
     */
    public static List<Integer> chooseWidths(String src, String defaultWidthsCsv, String reencodedWidthsCsv) {
        var extension = extensionOf(src);
        if (REENCODED_EXTENSIONS.contains(extension)) {
            var reencoded = parseWidths(reencodedWidthsCsv);
            if (reencoded.isEmpty()) {
                reencoded = parseWidths(DEFAULT_REENCODED_WIDTHS_CSV);
            }
            if (!reencoded.isEmpty()) {
                return reencoded;
            }
        }
        return parseWidths(defaultWidthsCsv);
    }

    /** Lowercase extension of the path, or an empty string when there is none. */
    public static String extensionOf(String src) {
        var path = pathOf(src);
        var slash = path.lastIndexOf('/');
        var dot = path.lastIndexOf('.');
        if (dot < 0 || dot < slash) {
            return "";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * True when {@code src} points at a same-origin Halo attachment that the
     * thumbnail endpoint can resize, and no size has been pinned yet.
     */
    public static boolean isResizable(String src, Set<String> skipExtensions) {
        if (src == null) {
            return false;
        }
        var trimmed = src.trim();
        if (trimmed.isEmpty() || trimmed.indexOf('#') >= 0) {
            return false;
        }
        // Only same-origin attachment paths: an absolute URL may point at a host
        // that does not understand Halo's query parameters.
        if (!trimmed.startsWith(UPLOAD_PREFIX)) {
            return false;
        }
        if (hasSizeParam(trimmed)) {
            return false;
        }
        var extension = extensionOf(trimmed);
        if (extension.isEmpty() || ALWAYS_SKIP.contains(extension)) {
            return false;
        }
        return skipExtensions == null || !skipExtensions.contains(extension);
    }

    /** True when the URL already carries a width/height/size parameter. */
    public static boolean hasSizeParam(String src) {
        var query = queryOf(src);
        if (query.isEmpty()) {
            return false;
        }
        for (var pair : query.split("&")) {
            var name = pair.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (SIZE_PARAMS.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** Append the width parameter to a same-origin src. */
    public static String withWidth(String src, int width) {
        var separator = src.indexOf('?') >= 0 ? "&" : "?";
        return src + separator + "width=" + width;
    }

    /** Build {@code "src?width=400 400w, src?width=800 800w"} for the given widths. */
    public static String buildSrcset(String src, List<Integer> widths) {
        var builder = new StringBuilder();
        for (var width : widths) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(withWidth(src, width)).append(' ').append(width).append('w');
        }
        return builder.toString();
    }

    private static String pathOf(String src) {
        var end = src.length();
        var questionMark = src.indexOf('?');
        var hash = src.indexOf('#');
        if (questionMark >= 0) {
            end = Math.min(end, questionMark);
        }
        if (hash >= 0) {
            end = Math.min(end, hash);
        }
        return src.substring(0, end);
    }

    private static String queryOf(String src) {
        var questionMark = src.indexOf('?');
        if (questionMark < 0) {
            return "";
        }
        var hash = src.indexOf('#');
        return hash > questionMark ? src.substring(questionMark + 1, hash) : src.substring(questionMark + 1);
    }
}
