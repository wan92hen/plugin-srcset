package io.github.wan92hen.plugin.srcset;

/**
 * Settings bound to the {@code basic} form declared in
 * {@code resources/extensions/settings.yaml}.
 *
 * <p>Plain getters and setters on purpose: the setting fetcher binds the config
 * map with Jackson, so a mutable bean with a no-argument constructor is the
 * least surprising shape.</p>
 *
 * @author wan92hen
 * @since 1.0.0
 */
public class SrcsetConfig {

    /** Comma separated list of candidate widths. */
    private String widths = "400,800,1200,1600";

    /** Value written to the {@code sizes} attribute. */
    private String sizes = "(max-width: 800px) 100vw, 768px";

    /** Extra extensions to skip, on top of the always-skipped ones. */
    private String skipExtensions = "";

    private Boolean enabled = Boolean.TRUE;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getWidths() {
        return widths;
    }

    public void setWidths(String widths) {
        this.widths = widths;
    }

    public String getSizes() {
        return sizes;
    }

    public void setSizes(String sizes) {
        this.sizes = sizes;
    }

    public String getSkipExtensions() {
        return skipExtensions;
    }

    public void setSkipExtensions(String skipExtensions) {
        this.skipExtensions = skipExtensions;
    }
}
