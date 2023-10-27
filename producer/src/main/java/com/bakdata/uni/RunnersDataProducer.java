package com.bakdata.uni;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import com.bakdata.kafka.KafkaProducerApplication;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.io.Resources;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.VoidSerializer;

@Slf4j
@Setter
@RequiredArgsConstructor
public class RunnersDataProducer extends KafkaProducerApplication {
    private final String fileName;
    private final ObjectMapper csvMapper = new CsvMapper().registerModule(new JavaTimeModule());

    public static void main(final String[] args) {
        startApplication(new RunnersDataProducer("data.csv"), args);
    }

    @Override
    protected void runApplication() {
        log.info("Starting runners producer...");
        // Your code goes here...!

        // 1. create a producer: Done
        try (final KafkaProducer<Void, RunnersRawData> producer = this.createProducer()) {
            // 2. read the CSV records line by line to JSON: Done
            final CsvSchema schema = CsvSchema.emptySchema().withHeader();
            final URL url = Resources.getResource(this.fileName);
            final MappingIterator<RunnersRawData> it = this.csvMapper
                    .readerFor(RunnersRawData.class)
                    .with(schema)
                    .readValues(url);
            while (it.hasNext()) {
                final RunnersRawData rawData = it.next();
                log.info("Runner id {} with is read and time is {}",
                        rawData.runnerId(),
                        rawData.runTime());
                // 3. send record to topic: Done
                final ProducerRecord<Void, RunnersRawData> producerRecord =
                        new ProducerRecord<>(this.getOutputTopic(), null, rawData);
                producer.send(producerRecord);
            }
            producer.flush();
        } catch (final IOException e) {
            log.error("Your file {} was not found! Give the correct path to the file!", this.fileName);
        }

    }

    @Override
    protected Properties createKafkaProperties() {
        final Properties kafkaProperties = super.createKafkaProperties();
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, VoidSerializer.class);
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class);
        kafkaProperties.setProperty(SCHEMA_REGISTRY_URL_CONFIG, this.getSchemaRegistryUrl());
        return kafkaProperties;
    }
}
