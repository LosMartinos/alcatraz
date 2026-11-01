package com.alcatraz.config;

import org.jgroups.JChannel;
import org.jgroups.conf.XmlConfigurator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class JGroupsConfig {
    /**
     * Creates the TCP/TCPPING channel using the configured addresses and timeouts.
     *
     * @param bindAddress local cluster address
     * @param port local cluster port
     * @param peers static cluster host list
     * @param failureTimeout failure-detection timeout in milliseconds
     * @param timeout timeout in milliseconds
     * @return configured cluster channel
     */
    @Bean
    public JChannel jChannel(
            @Value("${alcatraz.cluster.bind-address}") String bindAddress,
            @Value("${alcatraz.cluster.port}") int port,
            @Value("${alcatraz.cluster.peers}") String peers,
            @Value("${alcatraz.cluster.failure-timeout-ms}") long failureTimeout,
            @Value("${alcatraz.cluster.timeout-ms}") long timeout) throws Exception {
        // Allow two 1-second heartbeat intervals and 1.5 seconds to verify suspicion.
        if (failureTimeout < 2000 || timeout <= failureTimeout + 3500) {
            throw new IllegalArgumentException("Cluster timeout must exceed failure timeout by more than 3500 ms");
        }
        try (var input = new ClassPathResource("jgroups-tcp.xml").getInputStream()) {
            var config = XmlConfigurator.getInstance(input);
            for (var protocol : config.getProtocolStack()) {
                var properties = protocol.getProperties();
                switch (protocol.getProtocolName()) {
                    case "TCP" -> {
                        properties.put("bind_addr", bindAddress);
                        properties.put("bind_port", Integer.toString(port));
                    }
                    case "TCPPING" -> properties.put("initial_hosts", peers);
                    case "FD_ALL3" -> {
                        properties.put("timeout", Long.toString(failureTimeout));
                    }
                    default -> { }
                }
            }
            return new JChannel(config);
        }
    }
}
