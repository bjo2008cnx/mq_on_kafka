package pl.allegro.tech.hermes.consumers.consumer.offset;

import com.codahale.metrics.Timer;
import com.google.common.collect.Sets;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.allegro.tech.hermes.api.SubscriptionName;
import pl.allegro.tech.hermes.common.metric.HermesMetrics;
import pl.allegro.tech.hermes.consumers.consumer.receiver.MessageCommitter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 关于用于计算偏移量以实际提交的算法的注意事项。
 * 这个算法背后的想法是，我们想提交：标记为已提交的但不大于最小的机上偏移（最小机上偏移 - 1）
 * 重要的提示！这个类是Kafka OffsetCommiter，所以它以Kafka的方式感知偏移量。
 * 最重要的是在消费者重新启动时读取的第一个偏移标记消息 * (Most importantly committed offset marks message that is read as first on Consumer restart)
 * （偏移量包括读取和排他写）。
 * <p>
 * 消费者使用两个队列来报告消息状态：
 * * inflightOffsets：当前正在发送的消息偏移（机上）
 * * committedOffsets：准备好提交的消息偏移量
 * <p>
 * 此提交者类以inflightOffsets和failedToCommitOffsets集的形式保存内部状态。
 * inlfightOffsets是当前处于飞行状态的所有偏移量。
 * failedToCommitOffsets是在之前的算法迭代中无法提交的偏移量
 * <p>
 * 在预定时间段内，提交算法运行。它有三个阶段。
 * 第一阶段：加入队列并进行递减：提交+1以匹配Kafka提交定义将所有先前未提交的偏移从failedToCommitOffsets集合添加到committedOffsets并清除failedToCommitOffsets集合drain inflightOffset
 * 第二阶段是计算偏移： 计算每个订阅和分区的最大承诺偏移量; 为每个订阅和分区计算最小的机上偏移量
 * 第三阶段是选择为每个订阅/分区提交哪个偏移量。它是以下两项中的最小值
 * * maximum committed offset
 * * minimum inflight offset
 * <p>
 * 该算法非常简单，内存高效，可以在单线程中执行并且不引入锁。
 */
public class OffsetCommitter implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(OffsetCommitter.class);

    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * 提交时间间隔
     */
    private final int offsetCommitPeriodSeconds;

    /**
     * 待提交的队列？
     */
    private final OffsetQueue offsetQueue;


    private final MessageCommitter messageCommitter;

    private final HermesMetrics metrics;

    /**
     * 当前正在发送的消息偏移（已发送未确认的偏移）
     */
    private final Set<SubscriptionPartitionOffset> inflightOffsets = new HashSet<>();

    /**
     *
     */
    private final MpscArrayQueue<SubscriptionName> subscriptionsToCleanup = new MpscArrayQueue<>(1000);

    public OffsetCommitter(OffsetQueue offsetQueue, MessageCommitter messageCommitter, int offsetCommitPeriodSeconds, HermesMetrics metrics) {
        this.offsetQueue = offsetQueue;
        this.messageCommitter = messageCommitter;
        this.offsetCommitPeriodSeconds = offsetCommitPeriodSeconds;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        try (Timer.Context c = metrics.timer("offset-committer.duration").time()) {
            //已提交的偏移量需要首先排空，这样在飞行队列排空后不会出现新的已提交偏移量 - 这会导致已提交偏移停止
            // committed offsets need to be drained first so that there is no possibility of new committed offsets showing up after inflight queue is drained -
            // this would lead to stall in committing offsets
            ReducingConsumer committedOffsetsReducer = processCommittedOffsets();
            Map<SubscriptionPartition, Long> maxCommittedOffsets = committedOffsetsReducer.reduced;

            ReducingConsumer inflightOffsetReducer = processInflightOffsets(committedOffsetsReducer.all);
            Map<SubscriptionPartition, Long> minInflightOffsets = inflightOffsetReducer.reduced;

            int scheduledToCommit = 0;
            OffsetsToCommit offsetsToCommit = new OffsetsToCommit();
            for (SubscriptionPartition partition : Sets.union(minInflightOffsets.keySet(), maxCommittedOffsets.keySet())) {
                long offset = Math.min(minInflightOffsets.getOrDefault(partition, Long.MAX_VALUE), maxCommittedOffsets.getOrDefault(partition, Long.MAX_VALUE));
                if (offset >= 0 && offset < Long.MAX_VALUE) {
                    scheduledToCommit++;
                    offsetsToCommit.add(new SubscriptionPartitionOffset(partition, offset));
                }
            }

            messageCommitter.commitOffsets(offsetsToCommit);
            metrics.counter("offset-committer.committed").inc(scheduledToCommit);

            cleanupUnusedSubscriptions();
        } catch (Exception exception) {
            logger.error("Failed to run offset committer: {}", exception.getMessage(), exception);
        }
    }

    /**
     *
     * @return
     */
    private ReducingConsumer processCommittedOffsets() {
        ReducingConsumer committedOffsetsReducer = new ReducingConsumer(Math::max, c -> c + 1);
        offsetQueue.drainCommittedOffsets(committedOffsetsReducer);
        committedOffsetsReducer.resetModifierFunction();
        return committedOffsetsReducer;
    }

    /**
     *
     * @param committedOffsets
     * @return
     */
    private ReducingConsumer processInflightOffsets(Set<SubscriptionPartitionOffset> committedOffsets) {
        ReducingConsumer inflightOffsetReducer = new ReducingConsumer(Math::min);
        offsetQueue.drainInflightOffsets(o -> reduceIfNotCommitted(o, inflightOffsetReducer, committedOffsets));
        inflightOffsets.forEach(o -> reduceIfNotCommitted(o, inflightOffsetReducer, committedOffsets));

        inflightOffsets.clear();
        inflightOffsets.addAll(inflightOffsetReducer.all);

        return inflightOffsetReducer;
    }

    /**
     *
     * @param offset
     * @param inflightOffsetReducer
     * @param committedOffsets
     */
    private void reduceIfNotCommitted(SubscriptionPartitionOffset offset, ReducingConsumer inflightOffsetReducer, Set<SubscriptionPartitionOffset>
            committedOffsets) {
        if (!committedOffsets.contains(offset)) {
            inflightOffsetReducer.accept(offset);
        }
    }

    /**
     *
     * @param subscriptionName
     */
    public void removeUncommittedOffsets(SubscriptionName subscriptionName) {
        subscriptionsToCleanup.offer(subscriptionName);
    }

    /**
     *
     */
    private void cleanupUnusedSubscriptions() {
        Set<SubscriptionName> subscriptionNames = new HashSet<>();
        subscriptionsToCleanup.drain(subscriptionNames::add);
        for (Iterator<SubscriptionPartitionOffset> iterator = inflightOffsets.iterator(); iterator.hasNext(); ) {
            if (subscriptionNames.contains(iterator.next().getSubscriptionName())) {
                iterator.remove();
            }
        }
    }

    /**
     *
     */
    public void start() {
        scheduledExecutor.scheduleWithFixedDelay(this, offsetCommitPeriodSeconds, offsetCommitPeriodSeconds, TimeUnit.SECONDS);
    }

    /**
     *
     */
    public void shutdown() {
        scheduledExecutor.shutdown();
    }
}
