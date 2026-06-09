package org.chobit.knot.gateway.plugin;

public interface PluginHandler<T extends PluginDispatchContext> {

    PluginDescriptor descriptor();

    Class<T> contextType();

    void handle(PluginBindingView binding, T context);
}
