package org.chobit.knot.plugin;

import java.util.List;

public interface PluginBindingProvider {

    List<PluginBindingView> listBindings(PluginExtensionPoint extensionPoint, PluginStageCode stageCode);
}
