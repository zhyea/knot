package org.chobit.knot.plugin;

public interface PluginDispatcher {

    <T extends PluginDispatchContext> void dispatch(PluginExtensionPoint extensionPoint,
                                                    PluginStageCode stageCode,
                                                    T context);
}
