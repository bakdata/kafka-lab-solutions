package com.bakdata.uni;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import com.bakdata.kafka.KafkaStreamsApplication;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serdes.VoidSerde;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindowedKStream;
import org.apache.kafka.streams.kstream.TimeWindows;
import picocli.CommandLine;

@Slf4j
@Setter
public class WindowingApplication extends KafkaStreamsApplication {
    @CommandLine.Option(names = "--window-duration",
            description = "The size of the window in seconds. Must be larger than zero.")
    private Duration windowSize = Duration.ofSeconds(30);

    @CommandLine.Option(names = "--grace-period",
            description = "The grace period in millis to admit out-of-order events to a window. Must be non-negative.")
    private Duration gracePeriod = Duration.ofMillis(500);


    public static void main(final String[] args) {
        startApplication(new WindowingApplication(), args);
    }

    private static CountAndSum getCountAndSumOfHeartRate(final RunnersStatus value, final CountAndSum aggregate) {
        aggregate.setCount(aggregate.getCount() + 1);
        aggregate.setSum(aggregate.getSum() + value.getHeartRate());
        return aggregate;
    }

    @Override
    public void buildTopology(final StreamsBuilder streamsBuilder) {
        // create TimeWindow
        final TimeWindows windows = TimeWindows.ofSizeAndGrace(this.windowSize, this.gracePeriod);

        // Read the input stream from the input topic and define the time stamp
        final KStream<Void, RunnersStatus> inputStream = streamsBuilder
                .stream(this.getInputTopics(),
                        Consumed.with(new VoidSerde(), this.getRunnerStatusSerde())
                                .withTimestampExtractor(new RunTimeExtractor()));

        final SpecificAvroSerde<CountAndSum> countAndSumSerde = this.getCountAndSumSerde();

        // Your code goes here...!

        // 1. select the key you want to group the data with --> <runnerId>_<runSession>
        final KStream<String, RunnersStatus> selectedKeyStream =
                inputStream.selectKey((key, value) -> value.getRunnerId() + "_" + value.getSession());

        selectedKeyStream.peek((key, value) -> log.error(
                "I saw a record with key {} and value of heart rate {}", key, value.getHeartRate()
        ));

        // 2. group the key
        final KGroupedStream<String, RunnersStatus> groupedStream = selectedKeyStream
                .groupByKey(Grouped.with(Serdes.String(), this.getRunnerStatusSerde()));
        // 3. window your group
        final TimeWindowedKStream<String, RunnersStatus> windowedStream =
                groupedStream.windowedBy(windows);

        // [1, 2, 3, 4, 5]
        // sum = 0
        // element = 1 sum = sum + element = 1
        // element = 2 sum = sum + element = 3 
        // ...
        // element = 5 sum = sum + element = 15 
        // 4. aggregate the data. Use the count and sum function
        final KTable<String, CountAndSum> aggregated = windowedStream.aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> getCountAndSumOfHeartRate(value, aggregate),
                        Materialized.with(Serdes.String(), countAndSumSerde))
                .toStream()
                .selectKey((key, value) -> "%s_%s".formatted(key.key(), key.window().start()))
                .toTable(Materialized.with(Serdes.String(), countAndSumSerde));

        // 5. map your aggregated keys to this format --> <runnerId>_<runSession>_<windowsStartTime>
        final KTable<String, Double> averageHeartRate = aggregated.mapValues(value -> value.getSum() / value.getCount(),
                Named.as("Average-Heart-Rate"),
                Materialized.with(Serdes.String(), Serdes.Double()));

        // Sink the stream into the output topic
        averageHeartRate.toStream()
                .peek((key, value) -> log.error("Aggregated value for key {} is {}", key, value))
                .to(this.getOutputTopic(), Produced.with(Serdes.String(), Serdes.Double()));
    }

    @Override
    public Topology createTopology() {
        final Topology topology = super.createTopology();
        log.info("The topology is: \n {}", topology.describe());
        return topology;
    }

    @Override
    public String getUniqueAppId() {
        return String.format("windowing-app-%s", this.getOutputTopic());
    }

    @Override
    protected Properties createKafkaProperties() {
        final Properties kafkaConfig = super.createKafkaProperties();
        kafkaConfig.setProperty(SCHEMA_REGISTRY_URL_CONFIG, this.getSchemaRegistryUrl());
        return kafkaConfig;
    }

    private SpecificAvroSerde<CountAndSum> getCountAndSumSerde() {
        final SpecificAvroSerde<CountAndSum> serde = new SpecificAvroSerde<>();
        serde.configure(this.getSerdeConfig(), false);
        return serde;
    }

    private SpecificAvroSerde<RunnersStatus> getRunnerStatusSerde() {
        final SpecificAvroSerde<RunnersStatus> serde = new SpecificAvroSerde<>();
        serde.configure(this.getSerdeConfig(), false);
        return serde;
    }

    private Map<String, String> getSerdeConfig() {
        return Map.of(SCHEMA_REGISTRY_URL_CONFIG, this.getSchemaRegistryUrl());
    }

}
