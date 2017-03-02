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

package edu.harvard.gis.hhypermap.bopws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import io.dropwizard.Application
import io.dropwizard.configuration.*
import io.dropwizard.jersey.errors.ErrorMessage
import io.dropwizard.lifecycle.Managed
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.federecio.dropwizard.swagger.SwaggerBundle
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import org.apache.solr.common.params.MapSolrParams
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.slf4j.LoggerFactory
import java.time.format.DateTimeParseException
import java.util.*
import javax.servlet.DispatcherType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

fun main(args: Array<String>) = DwApplication().run(*args)

val log = LoggerFactory.getLogger(DwApplication::class.java.`package`.name)!!

/**
 * Dropwizard main entry for our web-service
 */
class DwApplication : Application<DwConfiguration>() {

  override fun initialize(bootstrap: Bootstrap<DwConfiguration>) {
    // Enable environment variables to override System properties
    bootstrap.configurationFactoryFactory = ConfigurationFactoryFactory { clazz, validator, objectMapper, prefix ->
      val propertyPrefix = "dw."
      object : YamlConfigurationFactory<DwConfiguration>(clazz, validator, objectMapper, propertyPrefix) {
        override fun build(node: JsonNode, path: String): DwConfiguration {
          // this logic is similar to what super.build does for sys props
          for ((prefName, value) in System.getenv()) {
            if (prefName.startsWith(propertyPrefix)) {
              val configName = prefName.substring(propertyPrefix.length)
              addOverride(node, configName, value)
            }
          }
          return super.build(node, path)
        }
      }
    }
    // Enable ${MYENVVAR} in the yaml
    bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
            bootstrap.configurationSourceProvider,
            EnvironmentVariableSubstitutor(false))

    // Swagger:
    bootstrap.addBundle(
            object : SwaggerBundle<DwConfiguration>() {
              override fun getSwaggerBundleConfiguration(configuration: DwConfiguration): SwaggerBundleConfiguration?
                      = configuration.swaggerBundleConfiguration
            }
    )
  }

  override fun run(configuration: DwConfiguration, environment: Environment) {
    // SOLR: Ensure we shut Solr down
    val solrClient = configuration.newSolrClient()
    environment.lifecycle().manage(object : Managed {
      override fun start() { }
      override fun stop() = solrClient.close()
    })

    // CORS
    if (configuration.cors != null) {
      log.info("Registering CORS filter")
      // https://stackoverflow.com/questions/25775364/enabling-cors-in-dropwizard-not-working
      val filter = environment.servlets().addFilter("CORS", CrossOriginFilter::class.java)
      filter.initParameters = configuration.cors
      filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType::class.java), true, "/*")
    }

    // Jersey: custom feature flags
    configuration.jersey?.forEach { environment.jersey().property(it.key, it.value) }

    environment.jersey().register(DTPExceptionMapper)

    environment.jersey().register(SearchWebService(solrClient, MapSolrParams(configuration.solrParams ?: Collections.emptyMap())))

    environment.healthChecks().register(DwHealthCheck.NAME, DwHealthCheck(solrClient))

    if (configuration.indent)
      environment.objectMapper.enable(SerializationFeature.INDENT_OUTPUT)
  }

  /** Map [DateTimeParseException] to 400 */
  @Provider
  object DTPExceptionMapper : ExceptionMapper<java.time.format.DateTimeParseException> {
    override fun toResponse(exception: DateTimeParseException?): Response? =
      Response.status(Response.Status.BAD_REQUEST)
              .entity(ErrorMessage(Response.Status.BAD_REQUEST.statusCode, exception.toString()))
              .build()

  }
}