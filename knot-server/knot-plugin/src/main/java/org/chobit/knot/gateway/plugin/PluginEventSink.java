package org.chobit.knot.gateway.plugin;

public interface PluginEventSink {

    void emit(PluginEvent event);
}
