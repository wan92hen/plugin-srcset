package io.github.wan92hen.plugin.srcset;

import static org.thymeleaf.templatemode.TemplateMode.HTML;

import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.ElementNames;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.MatchingElementName;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.dialect.ElementTagPostProcessor;

/**
 * Adds {@code srcset} (and {@code sizes}) to images that do not declare one yet,
 * pointing the candidates at Halo's own thumbnail endpoint
 * ({@code /upload/xxx?width=W}).
 *
 * <p>Registered as an {@link ElementTagPostProcessor}, so it sees every {@code <img>}
 * in the rendered theme — including images written directly in templates, not just
 * the ones coming from post content.</p>
 *
 * <p>The processor never throws and never blocks rendering: anything it cannot
 * handle stays exactly as it was.</p>
 *
 * @author wan92hen
 * @since 1.0.0
 */
@Component
public class SrcsetImageTagProcessor implements ElementTagPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SrcsetImageTagProcessor.class);

    /** Must match the setting form group declared in settings.yaml. */
    static final String SETTING_GROUP = "basic";

    private final ReactiveSettingFetcher settingFetcher;

    private final MatchingElementName imgElement;

    public SrcsetImageTagProcessor(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
        this.imgElement = MatchingElementName.forElementName(HTML, ElementNames.forHTMLName("img"));
    }

    @Override
    public Mono<IProcessableElementTag> process(ITemplateContext context, IProcessableElementTag tag) {
        if (!imgElement.matches(tag.getElementDefinition().getElementName())) {
            return Mono.empty();
        }
        // Whatever the theme or the editor already declared wins.
        if (tag.hasAttribute("srcset")) {
            return Mono.empty();
        }
        var src = Optional.ofNullable(tag.getAttribute("src"))
            .map(IAttribute::getValue)
            .filter(StringUtils::hasText);
        if (src.isEmpty()) {
            return Mono.empty();
        }
        var trimmedSrc = src.get().trim();
        // Small images (logo walls, badges) are worse off with any candidate.
        // They get a pinned single candidate instead, so that Halo's own
        // processor does not pick them up either.
        if (ThumbnailCandidates.isOptedOut(attributeValue(tag, ThumbnailCandidates.OPT_OUT_ATTRIBUTE))) {
            return pinned(context, tag, trimmedSrc);
        }
        return settingFetcher.fetch(SETTING_GROUP, SrcsetConfig.class)
            .defaultIfEmpty(new SrcsetConfig())
            .filter(SrcsetConfig::isEnabled)
            .flatMap(config -> build(context, tag, trimmedSrc, config));
    }

    private Mono<IProcessableElementTag> pinned(ITemplateContext context, IProcessableElementTag tag, String src) {
        try {
            return Mono.just(context.getModelFactory()
                .setAttribute(tag, "srcset", ThumbnailCandidates.pinnedSrcset(src)));
        } catch (RuntimeException e) {
            log.warn("Failed to pin {} to its original: {}", src, e.toString());
            return Mono.empty();
        }
    }

    private Mono<IProcessableElementTag> build(ITemplateContext context, IProcessableElementTag tag,
        String src, SrcsetConfig config) {
        Mono<IProcessableElementTag> result = Mono.empty();
        try {
            Set<String> extraSkip = ThumbnailCandidates.parseExtensions(config.getSkipExtensions());
            if (!ThumbnailCandidates.isResizable(src, extraSkip)) {
                return Mono.empty();
            }
            var widths = ThumbnailCandidates.chooseWidths(src, config.getWidths(),
                config.getReencodedWidths());
            if (widths.isEmpty()) {
                return Mono.empty();
            }
            var modelFactory = context.getModelFactory();
            var newTag = modelFactory.setAttribute(tag, "srcset",
                ThumbnailCandidates.buildSrcset(src, widths));
            // A sizes attribute the theme wrote itself is never overwritten.
            if (!tag.hasAttribute("sizes")) {
                var sizes = ThumbnailCandidates.sizesFor(attributeValue(tag, "width"), config.getSizes());
                if (sizes != null) {
                    newTag = modelFactory.setAttribute(newTag, "sizes", sizes);
                }
            }
            result = Mono.just(newTag);
        } catch (RuntimeException e) {
            // Rendering must never break because of this plugin.
            log.warn("Failed to add srcset to {}: {}", src, e.toString());
        }
        return result;
    }

    private static String attributeValue(IProcessableElementTag tag, String name) {
        return Optional.ofNullable(tag.getAttribute(name)).map(IAttribute::getValue).orElse(null);
    }
}
