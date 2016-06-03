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

import io.dropwizard.Application
import io.dropwizard.jersey.errors.ErrorMessage
import io.dropwizard.lifecycle.Managed
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.federecio.dropwizard.swagger.SwaggerBundle
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import java.time.format.DateTimeParseException
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

/**
 * Dropwizard main entry for our web-service
 */
class DwApplication : Application<DwConfiguration>() {

  override fun run(configuration: DwConfiguration, environment: Environment) {
    // Manage Solr
    val solrClient = configuration.newSolrClient()
    environment.lifecycle().manage(object : Managed {
      override fun start() { }
      override fun stop() = solrClient.close()
    })

    environment.healthChecks().register(DwHealthCheck.NAME, DwHealthCheck(solrClient))

    environment.jersey().register(DTPExceptionMapper)

    environment.jersey().register(SearchWebService(solrClient))
  }

  override fun initialize(bootstrap: Bootstrap<DwConfiguration>) {
    // Swagger:
    bootstrap.addBundle(
            object : SwaggerBundle<DwConfiguration>() {
              override fun getSwaggerBundleConfiguration(configuration: DwConfiguration): SwaggerBundleConfiguration?
                      = configuration.swaggerBundleConfiguration
            }
    )
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

fun main(args: Array<String>) = DwApplication().run(*args)
