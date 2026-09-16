package io.github.wan92hen.plugin.srcset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The decisions that must not regress: never emit a candidate the thumbnail
 * endpoint would refuse, and never touch anything outside {@code /upload/}.
 *
 * @author wan92hen
 * @since 1.0.0
 */
class ThumbnailCandidatesTest {

    private static final Set<String> NO_EXTRA_SKIP = Set.of();

    @Test
    @DisplayName("parseWidths keeps usable values in order and drops the rest")
    void parseWidths() {
        assertEquals(List.of(400, 800, 1200, 1600),
            ThumbnailCandidates.parseWidths("400,800,1200,1600"));
        assertEquals(List.of(800, 400), ThumbnailCandidates.parseWidths(" 800 , 400 "));
        assertEquals(List.of(800), ThumbnailCandidates.parseWidths("800,800"));
        assertEquals(List.of(800), ThumbnailCandidates.parseWidths("abc,800,-5,0,99999"));
        assertEquals(List.of(), ThumbnailCandidates.parseWidths(""));
        assertEquals(List.of(), ThumbnailCandidates.parseWidths(null));
    }

    @Test
    @DisplayName("parseExtensions normalises case and leading dots")
    void parseExtensions() {
        assertEquals(Set.of("gif", "svg"), ThumbnailCandidates.parseExtensions(".GIF, svg"));
        assertEquals(Set.of(), ThumbnailCandidates.parseExtensions(""));
        assertEquals(Set.of(), ThumbnailCandidates.parseExtensions(null));
    }

    @Test
    @DisplayName("extensionOf reads the path, ignoring query and fragment")
    void extensionOf() {
        assertEquals("webp", ThumbnailCandidates.extensionOf("/upload/a.WEBP"));
        assertEquals("png", ThumbnailCandidates.extensionOf("/upload/a.png?width=800"));
        assertEquals("", ThumbnailCandidates.extensionOf("/upload/no-extension"));
        assertEquals("", ThumbnailCandidates.extensionOf("/upload/dir.v2/file"));
    }

    @Test
    @DisplayName("only same-origin attachments without a pinned size are resizable")
    void resizable() {
        assertTrue(ThumbnailCandidates.isResizable("/upload/a.webp", NO_EXTRA_SKIP));
        assertTrue(ThumbnailCandidates.isResizable("/upload/a.PNG", NO_EXTRA_SKIP));
        assertTrue(ThumbnailCandidates.isResizable("/upload/a.jpg?v=2", NO_EXTRA_SKIP));

        // The endpoint answers 403 for GIF, so a candidate there would break the image.
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.gif", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.svg", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.ico", NO_EXTRA_SKIP));

        // Anything the settings ask to skip.
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.webp", Set.of("webp")));

        // Already sized, no extension, or not a Halo attachment path.
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.png?width=800", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.png?height=400", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.png?size=medium", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/upload/a.png#frag", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/upload/no-extension", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("https://cdn.example.com/a.png", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("/assets/img/a.png", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("a.png", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable("", NO_EXTRA_SKIP));
        assertFalse(ThumbnailCandidates.isResizable(null, NO_EXTRA_SKIP));
    }

    @Test
    @DisplayName("hasSizeParam finds width/height/size, case insensitively")
    void hasSizeParam() {
        assertTrue(ThumbnailCandidates.hasSizeParam("/upload/a.png?width=800"));
        assertTrue(ThumbnailCandidates.hasSizeParam("/upload/a.png?v=1&WIDTH=800"));
        assertTrue(ThumbnailCandidates.hasSizeParam("/upload/a.png?size=medium"));
        assertFalse(ThumbnailCandidates.hasSizeParam("/upload/a.png"));
        assertFalse(ThumbnailCandidates.hasSizeParam("/upload/a.png?v=1"));
        assertFalse(ThumbnailCandidates.hasSizeParam("/upload/a.png?thumbnail=1"));
    }

    @Test
    @DisplayName("WebP gets the narrower width list, other formats keep the full one")
    void chooseWidths() {
        // Measured: a large JPEG thumbnail of a WebP source is bigger than the
        // source, and retina browsers pick the widest candidate.
        assertEquals(List.of(400, 800),
            ThumbnailCandidates.chooseWidths("/upload/a.webp", "400,800,1200,1600", "400,800"));
        assertEquals(List.of(400, 800, 1200, 1600),
            ThumbnailCandidates.chooseWidths("/upload/a.png", "400,800,1200,1600", "400,800"));
        assertEquals(List.of(400, 800, 1200, 1600),
            ThumbnailCandidates.chooseWidths("/upload/a.jpg", "400,800,1200,1600", "400,800"));
        // A blank or unusable WebP list must keep the safe default, not widen:
        // an upgrade from a version without the setting stores an empty value.
        assertEquals(List.of(400, 800),
            ThumbnailCandidates.chooseWidths("/upload/a.webp", "400,600,800,1200", ""));
        assertEquals(List.of(400, 800),
            ThumbnailCandidates.chooseWidths("/upload/a.webp", "400,600,800,1200", null));
        assertEquals(List.of(400, 800),
            ThumbnailCandidates.chooseWidths("/upload/a.webp", "400,600,800,1200", "abc"));
        // Repeating the shared list explicitly is how you opt into all widths.
        assertEquals(List.of(400, 600, 800, 1200),
            ThumbnailCandidates.chooseWidths("/upload/a.webp", "400,600,800,1200", "400,600,800,1200"));
        // The two lists are independent: an empty shared list still leaves WebP
        // on its own default, and a format that keeps its format has nothing.
        assertEquals(List.of(400, 800), ThumbnailCandidates.chooseWidths("/upload/a.webp", "", ""));
        assertEquals(List.of(), ThumbnailCandidates.chooseWidths("/upload/a.png", "", ""));
    }

    @Test
    @DisplayName("buildSrcset appends width params and keeps existing ones")
    void buildSrcset() {
        assertEquals("/upload/a.webp?width=400 400w, /upload/a.webp?width=800 800w",
            ThumbnailCandidates.buildSrcset("/upload/a.webp", List.of(400, 800)));
        assertEquals("/upload/a.webp?v=2&width=800 800w",
            ThumbnailCandidates.buildSrcset("/upload/a.webp?v=2", List.of(800)));
        assertEquals("", ThumbnailCandidates.buildSrcset("/upload/a.webp", List.of()));
    }
}
