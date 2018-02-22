package pl.allegro.tech.hermes.consumers.consumer.offset;

import org.jctools.queues.MessagePassingQueue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 */
public class ReducingConsumer implements MessagePassingQueue.Consumer<SubscriptionPartitionOffset> {
    //
    private final BiFunction<Long, Long, Long> reductor;

    //
    private Function<Long, Long> modifier;

    //减少的偏移？
    final Map<SubscriptionPartition, Long> reduced = new HashMap<>();

    //全部偏移
    final Set<SubscriptionPartitionOffset> all = new HashSet<>();

    public ReducingConsumer(BiFunction<Long, Long, Long> reductor, Function<Long, Long> offsetModifier) {
        this.reductor = reductor;
        this.modifier = offsetModifier;
    }

    public ReducingConsumer(BiFunction<Long, Long, Long> reductor) {
        this(reductor, Function.identity());
    }

    public void resetModifierFunction() {
        // ？ 
        this.modifier = Function.identity();
    }

    @Override
    public void accept(SubscriptionPartitionOffset p) {
        all.add(p);
        reduced.compute(p.getSubscriptionPartition(), (k, v) -> {
            long offset = modifier.apply(p.getOffset());
            return v == null ? offset : reductor.apply(v, offset);
        });
    }
}