package edu.harvard.gis.hhypermap.etl

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer

class GenericAvroSerde : Serde<GenericRecord> {

  private val inner: Serde<GenericRecord>

  @JvmOverloads constructor(client: SchemaRegistryClient? = null, props: Map<String, *>? = null) {
    val deserializer =
            if (props == null) KafkaAvroDeserializer(client)
            else KafkaAvroDeserializer(client, props)
    inner = Serdes.serdeFrom<GenericRecord>(
            KafkaAvroSerializer(client) as Serializer<GenericRecord>,
            deserializer as Deserializer<GenericRecord>)
  }

  override fun serializer(): Serializer<GenericRecord> = inner.serializer()

  override fun deserializer(): Deserializer<GenericRecord> = inner.deserializer()

  override fun configure(configs: Map<String, *>, isKey: Boolean) {
    inner.serializer().configure(configs, isKey)
    inner.deserializer().configure(configs, isKey)
  }

  override fun close() {
    inner.serializer().close()
    inner.deserializer().close()
  }

}