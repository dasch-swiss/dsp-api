/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import akka.http.scaladsl.model.HttpRequest
import zio._

object LogAspect {

  /**
   * Extracts the correlation id from the HTTP request and adds it as a log annotation.
   *
   * @param req
   */
  def logAnnotateCorrelationId(
    req: HttpRequest
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](
        zio: ZIO[R, E, A]
      )(implicit trace: Trace): ZIO[R, E, A] =
        correlationId(req).flatMap(id => ZIO.logAnnotate("correlation-id", id)(zio))

      // TODO: get `X-Correlation-ID` header from the request (when the clients start sending it)
      def correlationId(req: HttpRequest): UIO[String] =
        Random.nextUUID.map(_.toString)
    }

  /**
   * Creates a span log annotation based on the provided label.
   *
   * @param label
   */
  def logSpan(
    label: String
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](zio: ZIO[R, E, A])(implicit
        trace: Trace
      ): ZIO[R, E, A] =
        ZIO.logSpan(label)(zio)
    }
}
