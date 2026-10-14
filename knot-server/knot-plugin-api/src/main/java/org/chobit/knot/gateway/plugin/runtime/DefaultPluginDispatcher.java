package org.chobit.knot.gateway.plugin.runtime;

import org.chobit.knot.gateway.plugin.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DefaultPluginDispatcher implements PluginDispatcher {

    private final PluginBindingProvider bindingProvider;
    private final List<PluginHandler<?>> handlers;

    public DefaultPluginDispatcher(PluginBindingProvider bindingProvider, List<PluginHandler<?>> handlers) {
        this.bindingProvider = bindingProvider;
        this.handlers = handlers;
    }

    @Override
    public <T extends PluginDispatchContext> void dispatch(PluginExtensionPoint extensionPoint,
                                                           PluginStageCode stageCode,
                                                           T context) {
        List<PluginBindingView> bindings = bindingProvider.listBindings(extensionPoint, stageCode).stream()
                .sorted(Comparator.comparing(binding -> binding.orderNo() == null ? Integer.MAX_VALUE : binding.orderNo()))
                .toList();
        for (PluginBindingView binding : bindings) {
            PluginHandler<T> handler = resolveHandler(binding, context);
            if (handler != null) {
                handler.handle(binding, context);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends PluginDispatchContext> PluginHandler<T> resolveHandler(PluginBindingView binding, T context) {
        for (PluginHandler<?> candidate : handlers) {
            PluginDescriptor descriptor = candidate.descriptor();
            if (!descriptor.capabilityCode().equalsIgnoreCase(binding.capabilityCode())) {
                continue;
            }
            if (descriptor.extensionPoint() != binding.extensionPoint()) {
                continue;
            }
            if (descriptor.stageCode() != binding.stageCode()) {
                continue;
            }
            if (!candidate.contextType().isInstance(context)) {
                continue;
            }
            return (PluginHandler<T>) candidate;
        }
        return null;
    }
}
