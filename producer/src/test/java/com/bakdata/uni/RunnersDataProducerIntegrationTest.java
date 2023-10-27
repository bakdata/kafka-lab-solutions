package com.bakdata.uni;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith;
import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.defaultClusterConfig;
import static net.mguenther.kafka.junit.Wait.delay;

import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import net.mguenther.kafka.junit.KeyValue;
import net.mguenther.kafka.junit.ReadKeyValues;
import net.mguenther.kafka.junit.TopicConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class RunnersDataProducerIntegrationTest {
    private static final int TIMEOUT_SECONDS = 10;
    private static final String OUTPUT_TOPIC = "raw-data";
    private static final String SCHEMA_REGISTRY_URL = "mock://test123";
    private final EmbeddedKafkaCluster kafkaCluster = provisionWith(defaultClusterConfig());
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
     * Create output topic and configure the producer.
     * <h2>Act</h2>
     * Start the producer. The producer will read the
     * <b>test-data.csv</b> file and produce each line to the output topic.
     * <h2>Assert</h2>
     * We read the produced records in the output topic and assert them. We should have 4 records.
     */
    @Test
    void shouldRunApp() throws InterruptedException {
        // Arrange
        this.kafkaCluster.createTopic(TopicConfig.withName(OUTPUT_TOPIC).useDefaults());
        final RunnersDataProducer dataProducer = this.setupApp();

        // Act
        dataProducer.run();
        delay(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert
        final List<KeyValue<Void, RunnersRawData>> values = this.kafkaCluster.read(
                ReadKeyValues
                        .from(OUTPUT_TOPIC, Void.class, RunnersRawData.class)
                        .with(SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL)
                        .with(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class)
                        .with(KafkaJsonDeserializerConfig.JSON_VALUE_TYPE, RunnersRawData.class.getName())
                        .build()
        );

        this.softly.assertThat(values)
                .hasSize(4)
                .allSatisfy(keyValue -> this.softly.assertThat(keyValue.getKey()).isNull())
                .allSatisfy(keyValue -> this.softly.assertThat(keyValue.getValue().runnerId()).isEqualTo("123"))
                .satisfiesExactly(
                        keyValue -> this.softly.assertThat(keyValue.getValue().runTime())
                                .isEqualTo(1697202000),
                        keyValue -> this.softly.assertThat(keyValue.getValue().runTime())
                                .isEqualTo(1697202001),
                        keyValue -> this.softly.assertThat(keyValue.getValue().runTime())
                                .isEqualTo(1697202002),
                        keyValue -> this.softly.assertThat(keyValue.getValue().runTime())
                                .isEqualTo(1697202003)
                );
    }

    private RunnersDataProducer setupApp() {
        final RunnersDataProducer producerApp = new RunnersDataProducer("test-data.csv");
        producerApp.setBrokers(this.kafkaCluster.getBrokerList());
        producerApp.setSchemaRegistryUrl(SCHEMA_REGISTRY_URL);
        producerApp.setOutputTopic(OUTPUT_TOPIC);
        producerApp.setStreamsConfig(Map.of(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000"));
        return producerApp;
    }
}
