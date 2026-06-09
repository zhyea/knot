package org.chobit.knot.gateway.plugin;

import java.util.List;

public interface PluginBindingProvider {

    List<PluginBindingView> listBindings(PluginExtensionPoint extensionPoint, PluginStageCode stageCode);
}
