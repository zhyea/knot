package org.chobit.knot.gateway.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knot.plugin.sink")
public class PluginSinkProperties {

    private final Kafka kafka = new Kafka();

    public Kafka getKafka() {
        return kafka;
    }

    public static class Kafka {
        private boolean enabled;
        private String topicPrefix = "knot.plugin";
        private String bootstrapServers = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTopicPrefix() {
            return topicPrefix;
        }

        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }
    }
}
