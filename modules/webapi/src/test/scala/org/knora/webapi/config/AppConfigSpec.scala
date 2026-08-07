/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import zio.ZIO
import zio.config.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.time.Duration

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.PasswordStrength

@RunWith(classOf[DspZTestJUnitRunner])
class AppConfigSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig       <- ZIO.service[AppConfig]
        jwtConfig       <- ZIO.service[JwtConfig]
        dspIngestConfig <- ZIO.service[DspIngestConfig]
      } yield {
        assertTrue(
          appConfig.sipi.timeout == Duration.ofSeconds(120),
          appConfig.triplestore.queryTimeout == Duration.ofSeconds(20),
          appConfig.triplestore.gravsearchTimeout == Duration.ofSeconds(120),
          appConfig.triplestore.searchTimeout == Duration.ofSeconds(60),
          appConfig.v2.fulltextSearch.probe.cap == 250000,
          appConfig.v2.fulltextSearch.probe.cacheCapacity == 1024,
          appConfig.v2.fulltextSearch.probe.cacheTtl == Duration.ofMinutes(10),
          appConfig.v2.fulltextSearch.probe.maxConcurrent == 8,
          appConfig.bcryptPasswordStrength == PasswordStrength.unsafeFrom(12).value,
          appConfig.instrumentationServerConfig.interval == Duration.ofSeconds(5),
          appConfig.filePermissionCache.ttl == Duration.ofMinutes(2),
          appConfig.filePermissionCache.capacity == 10000,
          appConfig.triplestore.sparqlPassthrough.timeout == Duration.ofSeconds(120),
          appConfig.triplestore.sparqlPassthrough.maxRequestBodyBytes == 1048576,
          appConfig.triplestore.sparqlPassthrough.maxResponseBytes == 67108864,
          appConfig.triplestore.sparqlPassthrough.maxConcurrentCalls == 8,
          dspIngestConfig.audience == "http://localhost:3340",
          dspIngestConfig.baseUrl == "http://localhost:3340",
          dspIngestConfig.externalBaseUrl == "http://localhost:3340",
          jwtConfig.expiration == java.time.Duration.ofDays(30),
          jwtConfig.issuer.contains("0.0.0.0:3333"),
          jwtConfig.issuerAsString() == "0.0.0.0:3333",
        )
      }
    }.provideLayer(AppConfig.layer),
    test("reject a file-permission-cache ttl that is not positive") {
      loadAppConfigWith("app.file-permission-cache.ttl = 0 seconds").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a file-permission-cache ttl above the 10 minute staleness guard") {
      loadAppConfigWith("app.file-permission-cache.ttl = 11 minutes").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a file-permission-cache capacity below 1") {
      loadAppConfigWith("app.file-permission-cache.capacity = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("the sparql passthrough flag is off in the built-in configuration (fail-closed)") {
      // REQ-3.2: a deployment that omits the env override must leave the route absent.
      loadAppConfigWith("").orDie.map(config => assertTrue(!config.allowSparqlPassthrough))
    },
    test("the sparql passthrough flag is enabled by the string true") {
      loadAppConfigWith("app.allow-sparql-passthrough = \"true\"").orDie
        .map(config => assertTrue(config.allowSparqlPassthrough))
    },
    test("a sparql passthrough flag value that is not a boolean is rejected at load, never treated as enabled") {
      // A typo in the env override must fail startup rather than silently resolve either way.
      loadAppConfigWith("app.allow-sparql-passthrough = \"enabled\"").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a sparql passthrough timeout that is not positive") {
      loadAppConfigWith("app.triplestore.sparql-passthrough.timeout = 0 seconds").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a sub-second sparql passthrough timeout, which would reach the store truncated to zero") {
      // The value is sent to the store in whole seconds, so anything under 1s would arrive as `timeout=0` and
      // silently remove the store-side cancellation, while the API-side deadline still bounded the call.
      loadAppConfigWith("app.triplestore.sparql-passthrough.timeout = 500 millis").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("accept a sparql passthrough timeout of exactly one second") {
      loadAppConfigWith("app.triplestore.sparql-passthrough.timeout = 1 second").orDie
        .map(config => assertTrue(config.triplestore.sparqlPassthrough.timeout == Duration.ofSeconds(1)))
    },
    test("reject a sparql passthrough response ceiling below 1") {
      loadAppConfigWith("app.triplestore.sparql-passthrough.max-response-bytes = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a sparql passthrough concurrency backstop below 1") {
      loadAppConfigWith("app.triplestore.sparql-passthrough.max-concurrent-calls = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a sparql passthrough request-body cap below 1") {
      loadAppConfigWith("app.triplestore.sparql-passthrough.max-request-body-bytes = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a search-timeout that is not positive") {
      loadAppConfigWith("app.triplestore.search-timeout = 0 seconds").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a search-timeout above the gravsearch-timeout") {
      loadAppConfigWith("app.triplestore.search-timeout = 121 seconds").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a probe cap below 1") {
      loadAppConfigWith("app.v2.fulltext-search.probe.cap = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a probe cache-capacity below 1") {
      loadAppConfigWith("app.v2.fulltext-search.probe.cache-capacity = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a probe cache-ttl that is not positive") {
      loadAppConfigWith("app.v2.fulltext-search.probe.cache-ttl = 0 seconds").exit
        .map(exit => assertTrue(exit.isFailure))
    },
    test("reject a probe max-concurrent below 1") {
      loadAppConfigWith("app.v2.fulltext-search.probe.max-concurrent = 0").exit
        .map(exit => assertTrue(exit.isFailure))
    },
  )

  // Loads the full application.conf, overriding the given HOCON keys, so a validation failure isolates to the override.
  private def loadAppConfigWith(overrides: String) =
    read(
      AppConfig.config from TypesafeConfigProvider.fromTypesafeConfig(
        ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load()).getConfig("app").resolve,
      ),
    )
}
