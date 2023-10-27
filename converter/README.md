# Converter

In this Kafka Streams application we will consume data from the `raw-data` topic and convert the JSON schema to a
compact [Avro schema](https://github.com/bakdata/kafka-lab/tree/main/common/src/main/avro). Further, we will remove
unnecessary fields (longitude, latitude, attitude) and keep information we
need only.

![img.png](img.png)

## Resources

- [Streams bootstrap Kafka Streams](https://github.com/bakdata/streams-bootstrap#kafka-streams)
- [Kafka Error Handling](https://github.com/bakdata/kafka-error-handling)
- [Kafka Streams basic operations](https://developer.confluent.io/courses/kafka-streams/basic-operations/)
- [mapValues](https://kafka.apache.org/20/javadoc/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-)
