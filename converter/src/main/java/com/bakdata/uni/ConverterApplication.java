package com.bakdata.uni;

import static com.bakdata.kafka.ErrorCapturingValueMapper.captureErrors;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import com.bakdata.kafka.AvroDeadLetterConverter;
import com.bakdata.kafka.DeadLetter;
import com.bakdata.kafka.KafkaStreamsApplication;
import com.bakdata.kafka.ProcessedValue;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;

@Slf4j
public class ConverterApplication extends KafkaStreamsApplication {
    public static void main(final String[] args) {
        startApplication(new ConverterApplication(), args);
    }

    private static RunnersStatus toRunnersStatus(final RunnersRawData rawData) {
        return RunnersStatus.newBuilder()
                .setRunnerId(rawData.runnerId())
                .setSession(rawData.session().toLowerCase())
                .setRunTime(rawData.runTime())
                .setDistance((int) rawData.distance())
                .setHeartRate(rawData.heartRate())
                .setSpeed(rawData.speed())
                .build();
    }

    @Override
    public void buildTopology(final StreamsBuilder streamsBuilder) {
        // Configure value SerDe
        final Serde<RunnersRawData> valueSerde = new KafkaJsonSchemaSerde<>();
        final Map<String, String> config = Map.of(SCHEMA_REGISTRY_URL_CONFIG, this.getSchemaRegistryUrl(),
                KafkaJsonDeserializerConfig.JSON_VALUE_TYPE, RunnersRawData.class.getName());
        valueSerde.configure(config, false);

        // Your code goes here...!

        // 1. Read the data from the input topic
        final KStream<Void, RunnersRawData> inputStream = streamsBuilder
                .stream(this.getInputTopics(), Consumed.with(Serdes.Void(), valueSerde));

        // 2. Map the incoming stream of RunnersRawData to RunnersStatus using the function toRunnersStatus
        final KStream<Void, ProcessedValue<RunnersRawData, RunnersStatus>> mappedWithErrors = inputStream
                .mapValues(captureErrors(ConverterApplication::toRunnersStatus));

        // 3. Bonus: Handle error using Kafka Error Handling

        // 3.a Handling the error stream
        final KStream<Void, DeadLetter> deadLetterKStream = mappedWithErrors.flatMapValues(ProcessedValue::getErrors)
                .processValues(AvroDeadLetterConverter.asProcessor(
                        "Could not map runner's raw data into runner's status! Check your data please! :)"));
        deadLetterKStream.to(this.getErrorTopic());

        // 3.b Handling the output stream
        final KStream<Void, RunnersStatus> mapValues =
                mappedWithErrors.flatMapValues(ProcessedValue::getValues);
        mapValues.to(this.getOutputTopic());
    }

    @Override
    public String getUniqueAppId() {
        return String.format("converter-app-%s", this.getOutputTopic());
    }
}
