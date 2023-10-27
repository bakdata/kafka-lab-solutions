package com.bakdata.uni;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith;
import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.defaultClusterConfig;
import static net.mguenther.kafka.junit.Wait.delay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import net.mguenther.kafka.junit.KeyValue;
import net.mguenther.kafka.junit.ReadKeyValues;
import net.mguenther.kafka.junit.SendKeyValuesTransactional;
import net.mguenther.kafka.junit.TopicConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.VoidDeserializer;
import org.apache.kafka.common.serialization.VoidSerializer;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class ConverterApplicationIntegrationTest {
    private static final String ERROR_TOPIC = "error-topic";
    private static final String INPUT = "raw-runner-data";
    private static final String OUTPUT_TOPIC = "runner-status";
    private static final String SCHEMA_REGISTRY_URL = "mock://test123";
    private static final int TIMEOUT_SECONDS = 5;
    private final EmbeddedKafkaCluster kafkaCluster = provisionWith(defaultClusterConfig());
    private final ObjectMapper objectMapper = new ObjectMapper();
    @InjectSoftAssertions
    private SoftAssertions softly;

    @BeforeEach
    void setup() {
        this.kafkaCluster.start();
    }

    @AfterEach
    void teardown() {
        this.kafkaCluster.stop();
    }

    /**
     * <h2>Arrange</h2>
     * Create output topic and configure the streams application.
     * <h2>Act</h2>
     * Start the streams application. The {@link #produceDataToInputTopic()} produce a single record to the input topic.
     * The streams app consumes the record and process it and produces the result to the output topic.
     * <h2>Assert</h2>
     * We read the output topic record and assert them. We should have 1 record with the given specifications.
     */
    @Test
    void shouldRunApp() throws InterruptedException {
        // Arrange
        this.kafkaCluster.createTopic(TopicConfig.withName(OUTPUT_TOPIC).useDefaults());
        final ConverterApplication app = this.setupApp();

        // Act
        final Thread runThread = new Thread(app);
        runThread.start();

        this.produceDataToInputTopic();

        delay(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert
        final List<KeyValue<Void, RunnersStatus>> values = this.kafkaCluster.read(
                ReadKeyValues
                        .from(OUTPUT_TOPIC, Void.class, RunnersStatus.class)
                        .with(SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL)
                        .with(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, VoidDeserializer.class)
                        .with(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SpecificAvroDeserializer.class)
                        .build()
        );

        this.softly.assertThat(values)
                .hasSize(1)
                .first()
                .satisfies(
                        keyValue -> {
                            this.softly.assertThat(keyValue.getKey())
                                    .isNull();
                            final RunnersStatus runnersStatus = keyValue.getValue();
                            this.softly.assertThat(runnersStatus.getRunnerId())
                                    .isEqualTo("123");
                            this.softly.assertThat(runnersStatus.getRunTime())
                                    .isEqualTo(100);
                            this.softly.assertThat(runnersStatus.getDistance())
                                    .isEqualTo(334);
                            this.softly.assertThat(runnersStatus.getHeartRate())
                                    .isEqualTo(170);
                            this.softly.assertThat(runnersStatus.getSpeed())
                                    .isEqualTo(1.437000036);
                        }
                );
    }

    private void produceDataToInputTopic() throws InterruptedException {
        final RunnersRawData rawData = new RunnersRawData(
                "123",
                "XYZ",
                100,
                23.75959083,
                23.75959083,
                97.80000305,
                334.980011,
                170,
                1.437000036
        );

        final KeyValue<Void, JsonNode> keyValue = new KeyValue<>(null, this.objectMapper.valueToTree(rawData));
        final SendKeyValuesTransactional<Void, JsonNode> sendRequest = SendKeyValuesTransactional
                .inTransaction(INPUT, List.of(keyValue))
                .with(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, VoidSerializer.class)
                .with(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class)
                .with(SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL)
                .build();
        this.kafkaCluster.send(sendRequest);
    }

    private ConverterApplication setupApp() {
        final ConverterApplication application = new ConverterApplication();
        application.setBrokers(this.kafkaCluster.getBrokerList());
        application.setSchemaRegistryUrl(SCHEMA_REGISTRY_URL);
        application.setInputTopics(List.of(INPUT));
        application.setOutputTopic(OUTPUT_TOPIC);
        application.setErrorTopic(ERROR_TOPIC);
        application.setStreamsConfig(Map.of(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000"));
        return application;
    }
}
