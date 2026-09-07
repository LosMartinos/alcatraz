package com.alcatraz.config;

import org.jgroups.JChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JGroupsConfig {

    @Bean
    public JChannel jChannel() throws Exception {
        // deliberately NOT connected here -- ClusterMembershipService sets itself
        // as the Receiver and calls connect() in its own @PostConstruct, so no
        // view/message can arrive before something is listening for it
        return new JChannel(); // default UDP stack; point at a custom config file later if needed
    }
}