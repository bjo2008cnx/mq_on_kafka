package pl.allegro.tech.hermes.consumers.consumer.offset;

import pl.allegro.tech.hermes.api.SubscriptionName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 待提交的偏移量
 */
public class OffsetsToCommit {

    private final Map<SubscriptionName, Set<SubscriptionPartitionOffset>> offsets = new HashMap<>();

    /**
     * 增加待提交项
     * @param offset
     * @return
     */
    public OffsetsToCommit add(SubscriptionPartitionOffset offset) {
        Set<SubscriptionPartitionOffset> subscriptionOffsets = offsets.get(offset.getSubscriptionName());
        if (subscriptionOffsets == null) {
            subscriptionOffsets = new HashSet<>();
            offsets.put(offset.getSubscriptionName(), subscriptionOffsets);
        }
        subscriptionOffsets.add(offset);
        return this;
    }

    public Set<SubscriptionName> subscriptionNames() {
        return offsets.keySet();
    }

    public Set<SubscriptionPartitionOffset> batchFor(SubscriptionName subscriptionName) {
        return offsets.get(subscriptionName);
    }
}
