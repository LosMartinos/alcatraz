package com.alcatraz.replication;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jgroups.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ClusterMembershipService implements Receiver {

    private static final Logger logger = LoggerFactory.getLogger(ClusterMembershipService.class);

    @Value("${alcatraz.cluster.name:alcatraz-registration-cluster}")
    private String clusterName;

    private final JChannel channel;
    private final ReplicationListener replicationListener;

    private volatile View currentView;

    public ClusterMembershipService(JChannel channel, ReplicationListener replicationListener) {
        this.channel = channel;
        this.replicationListener = replicationListener;
    }

    @PostConstruct
    public void connect() throws Exception {
        channel.setReceiver(this);
        channel.setDiscardOwnMessages(true); // primary already applied its own write locally
        channel.connect(clusterName);
        logger.info("Connected to cluster '{}' as {}", clusterName, channel.getAddress());
    }

    @PreDestroy
    public void disconnect() {
        channel.close();
    }

    // JGroups calls this on every join AND every leave/crash -- this single
    // callback is what makes failover automatic, no separate election needed
    @Override
    public void viewAccepted(View newView) {
        this.currentView = newView;
        logger.info("View changed: {} members, primary is now {}",
                newView.size(), newView.getMembers().getFirst());
    }

    // actual replicated game-state events (not membership) land here,
    // handed off to whoever applies them to GameState
    @Override
    public void receive(Message msg) {
        replicationListener.onMessage(msg);
    }

    public boolean isPrimary() {
        if (currentView == null || channel.getAddress() == null) {
            return false; // not connected / no view yet -- never assume primary before we know
        }
        Address oldest = currentView.getMembers().get(0);
        return oldest.equals(channel.getAddress());
    }

    public void broadcast(ReplicationEvent event) {
        try {
            channel.send(new ObjectMessage(null, event)); // null destination = send to all members
        } catch (Exception e) {
            throw new RuntimeException("failed to replicate event: " + event, e);
        }
    }
}