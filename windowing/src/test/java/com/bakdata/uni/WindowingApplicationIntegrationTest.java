package com.bakdata.uni;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith;
import static net.mguenther.kafka.junit.Wait.delay;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.io.Resources;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig;
import net.mguenther.kafka.junit.KeyValue;
import net.mguenther.kafka.junit.ReadKeyValues;
import net.mguenther.kafka.junit.SendKeyValuesTransactional;
import net.mguenther.kafka.junit.TopicConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.DoubleDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.VoidSerializer;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class WindowingApplicationIntegrationTest {
    private static final int TIMEOUT_SECONDS = 5;
    private static final String INPUT_TOPIC = "runners-status";
    private static final String OUTPUT_TOPIC = "windowed-analytics";
    private static final EmbeddedKafkaCluster kafkaCluster =
            provisionWith(EmbeddedKafkaClusterConfig.defaultClusterConfig());
    private static final String SCHEMA_REGISTRY_URL = "mock://test123";
    private final ObjectMapper csvMapper = new CsvMapper();
    @InjectSoftAssertions
    private SoftAssertions softly;

    @BeforeAll
    static void setup() {
        kafkaCluster.start();
    }

    @AfterAll
    static void tearDown() {
        kafkaCluster.stop();
    }

    private static WindowingApplication createApp() {
        final WindowingApplication app = new WindowingApplication();
        app.setSchemaRegistryUrl(SCHEMA_REGISTRY_URL);
        app.setBrokers(kafkaCluster.getBrokerList());
        app.setInputTopics(List.of(INPUT_TOPIC));
        app.setOutputTopic(OUTPUT_TOPIC);
        app.setProductive(false);
        app.setWindowSize(Duration.ofSeconds(6));
        app.setGracePeriod(Duration.ofMillis(0));
        return app;
    }

    /**
     * <h2>Arrange</h2>
     * Create output topic and configure the windowing application. The window size is set to 6 seconds.
     * <h2>Act</h2>
     * Start the streams application. The {@link #produceDataToInputTopic()} will read the
     * <b>test-data.csv</b> file and produce each line to the input topic. The streams app will consume the records and
     * produce them to the output topic. The are 600 data records in the CVS, each produced in a second.
     * <h2>Assert</h2>
     * We read the processed records in the output topic and assert them. We should have 11 windows (records). In the
     * assertion you see the first three windows (records).
     */
    @Test
    void shouldRunApp() throws InterruptedException, IOException {
        // Arrange
        kafkaCluster.createTopic(TopicConfig.withName(OUTPUT_TOPIC).useDefaults());
        final WindowingApplication app = createApp();

        // Act
        final Thread runThread = new Thread(app);
        runThread.start();
        this.produceDataToInputTopic();

        delay(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert
        final List<KeyValue<String, Double>> values = kafkaCluster.read(
                ReadKeyValues
                        .from(OUTPUT_TOPIC, String.class, Double.class)
                        .with(SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL)
                        .with(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                        .with(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DoubleDeserializer.class)
                        .build()
        );

        this.softly.assertThat(values)
                .hasSize(11)
                .anySatisfy(
                        keyValue -> {
                            this.softly.assertThat(keyValue.getKey())
                                    .isNotNull()
                                    .isEqualTo("123_XYZ_1697202000000");
                            this.softly.assertThat(keyValue.getValue())
                                    .isNotNull()
                                    .isEqualTo(126.5);
                        })
                .anySatisfy(
                        keyValue2 -> {
                            this.softly.assertThat(keyValue2.getKey())
                                    .isNotNull()
                                    .isEqualTo("123_XYZ_1697202006000");
                            this.softly.assertThat(keyValue2.getValue())
                                    .isNotNull()
                                    .isEqualTo(134.83333333333334);
                        })
                .anySatisfy(
                        keyValue3 -> {
                            this.softly.assertThat(keyValue3.getKey())
                                    .isNotNull()
                                    .isEqualTo("123_XYZ_1697202012000");
                            this.softly.assertThat(keyValue3.getValue())
                                    .isNotNull()
                                    .isEqualTo(141.0);
                        }
                );
    }

    private void produceDataToInputTopic() throws InterruptedException, IOException {
        final URL url = Resources.getResource("test-data.csv");
        final CsvSchema schema = CsvSchema.emptySchema().withHeader();
        final Collection<KeyValue<Void, RunnersStatus>> records = new ArrayList<>();
        try (final MappingIterator<RunnersStatus> mappingIterator =
                this.csvMapper.readerFor(RunnersStatus.class)
                        .with(schema)
                        .readValues(url)) {
            while (mappingIterator.hasNext()) {
                final RunnersStatus runnersStatus = mappingIterator.next();
                final KeyValue<Void, RunnersStatus> keyValue = new KeyValue<>(null, runnersStatus);
                records.add(keyValue);
            }
            final SendKeyValuesTransactional<Void, RunnersStatus> sendRequest = SendKeyValuesTransactional
                    .inTransaction(INPUT_TOPIC, records)
                    .with(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, VoidSerializer.class)
                    .with(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SpecificAvroSerializer.class)
                    .with(SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL)
                    .build();
            kafkaCluster.send(sendRequest);
        }


    }
}
