package com.bakdata.uni;

import java.time.Instant;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class RunTimeExtractor implements TimestampExtractor {

    @Override
    public long extract(final ConsumerRecord<Object, Object> record, final long partitionTime) {
        final RunnersStatus runnersStatus = (RunnersStatus) record.value();
        final Long time = Optional.of(runnersStatus.getRunTime())
                .orElse(partitionTime);
        return Instant.ofEpochSecond(time).toEpochMilli();
    }
}
