package org.knora.webapi.slice.infrastructure

import zio._
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.OpenTelemetry
import io.sentry.Sentry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import java.util.HashMap
import io.sentry.SentryLevel

object OpenTelemetryTracer extends ZIOAppDefault {

  Sentry.init { options =>
    options.setDsn("https://ab837a15c0ff376d1017ae8adaa7b946@o4506122165747712.ingest.us.sentry.io/4509127871496192");
    options.setTracesSampleRate(1.0);
  };

  val sdkConfig = for {
    sdk <- ZIO.attempt {
             AutoConfiguredOpenTelemetrySdk
               .builder()
               .setResultAsGlobal()
               .addPropertiesSupplier { () =>
                 val properties = new HashMap[String, String]()
                 properties.put("otel.logs.exporter", "none");
                 properties.put("otel.metrics.exporter", "none");
                 properties.put("otel.traces.exporter", "none");
                 properties
               }
               .build()
           }
  } yield sdk.getOpenTelemetrySdk()

  override def run = ZIO.serviceWithZIO[Tracing] { tracing =>
    for {
      _ <- tracing.root("ZIO root span") {
             for {
               _ <- ZIO.logInfo("Hello from ZIO in root span")
               _ <- tracing.span("ZIO span 1") {
                      for {
                        _ <- ZIO.logInfo("Hello from ZIO inside span")
                        _ <- ZIO.unit.delay(1.second)
                        _ <- ZIO.logInfo("Hello from ZIO inside span after waiting")
                        _ <- ZIO.succeed(
                               Sentry.addBreadcrumb("Successfully finished task of first span"),
                             )
                      } yield ()
                    }
               _ <- tracing.span("ZIO span 2") {
                      for {
                        _ <- ZIO.logInfo("Hello from ZIO inside span")
                        _ <- ZIO.unit.delay(1.second)
                        _ <- ZIO.logInfo("Hello from ZIO inside span after waiting")
                        _ <- ZIO.succeed(
                               Sentry.addBreadcrumb(
                                 "Successfully finished task of second span",
                               ),
                             )
                      } yield ()
                    }
               _ <- ZIO.succeed(
                      Sentry.captureMessage("Task finished", SentryLevel.INFO),
                    )
             } yield ()

           }
    } yield ()
      
  }.provide(
        OpenTelemetry.custom(sdkConfig),
        OpenTelemetry.tracing("foo-app"),
        OpenTelemetry.contextZIO,
      )
}
