package org.chobit.knot.gateway.plugin.runtime;

import org.chobit.knot.gateway.plugin.PluginEvent;
import org.chobit.knot.gateway.plugin.PluginEventSink;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class CompositePluginEventSink implements PluginEventSink {

    private final List<PluginEventSink> delegates;

    public CompositePluginEventSink(List<PluginEventSink> delegates) {
        this.delegates = delegates.stream()
                .filter(delegate -> !(delegate instanceof CompositePluginEventSink))
                .toList();
    }

    @Override
    public void emit(PluginEvent event) {
        for (PluginEventSink delegate : delegates) {
            delegate.emit(event);
        }
    }
}
