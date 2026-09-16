package io.github.wan92hen.plugin.srcset;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * <p>The plugin has no lifecycle work of its own: the feature lives entirely in
 * {@link SrcsetImageTagProcessor}.</p>
 *
 * @author wan92hen
 * @since 1.0.0
 */
@Component
public class SrcsetPlugin extends BasePlugin {

    public SrcsetPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
