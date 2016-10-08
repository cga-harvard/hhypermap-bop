/*
 * Copyright 2016 President and Fellows of Harvard College
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.harvard.gis.hhypermap.bop.kafkastreamsbase

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.core.util.BufferRecycler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import org.msgpack.jackson.dataformat.MessagePackFactory

class JsonSerde : Serde<TreeNode> {

  override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) { }

  override fun close() { }

  override fun deserializer(): Deserializer<TreeNode> = JsonDeserializer()

  override fun serializer(): Serializer<TreeNode> = JsonSerializer()

  companion object {
    fun newObjectMapper() : ObjectMapper {
      // TODO make MessagePack or not optional. Maybe make factory class configurable.
      // WARNING: this approach is not thread-safe, and I think that's okay since it *appears*
      //  Serde's are used in a way in which it doesn't need to be.
      val jsonFactory = object : MessagePackFactory() {
        val bufferRecycler = BufferRecycler() // re-used -- an optimization
        override fun _getBufferRecycler(): BufferRecycler = bufferRecycler
      }
      return ObjectMapper(jsonFactory)
    }
  }

  class JsonDeserializer: Deserializer<TreeNode> {
    val objectMapper = newObjectMapper()

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) { }

    override fun close() { }

    override fun deserialize(topic: String, data: ByteArray): TreeNode =
            objectMapper.readValue(data, JsonNode::class.java)
  }

  class JsonSerializer : Serializer<TreeNode> {
    val objectMapper = newObjectMapper()

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) { }

    override fun close() { }

    override fun serialize(topic: String, data: TreeNode): ByteArray =
            objectMapper.writeValueAsBytes(data)
  }

}
