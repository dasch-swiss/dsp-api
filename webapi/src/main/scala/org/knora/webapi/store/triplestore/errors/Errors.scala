/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.errors

import dsp.errors.InternalServerException
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError

/**
 * An abstract class for exceptions indicating that something went wrong with the triplestore.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
abstract class TriplestoreException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

/**
 * Indicates that the network connection to the triplestore failed.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
final case class TriplestoreConnectionException(message: String, cause: Option[Throwable] = None)
    extends TriplestoreException(message, cause)

/**
 * Indicates that a read timeout occurred while waiting for data from the triplestore.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
final case class TriplestoreTimeoutException(message: String, cause: Option[Throwable] = None)
    extends TriplestoreException(message, cause)

object TriplestoreTimeoutException {
  def apply(message: String, cause: Throwable): TriplestoreTimeoutException =
    TriplestoreTimeoutException(message, Some(cause))

  def apply(message: String): TriplestoreTimeoutException =
    TriplestoreTimeoutException(message, None)
}

/**
 * Indicates that we tried using a feature which is unsuported by the selected triplestore.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
final case class TriplestoreUnsupportedFeatureException(message: String, cause: Option[Throwable] = None)
    extends TriplestoreException(message, cause)

object TriplestoreUnsupportedFeatureException {
  def apply(message: String, e: Throwable): TriplestoreUnsupportedFeatureException =
    TriplestoreUnsupportedFeatureException(message, Some(e))
}

/**
 * Indicates that something inside the Triplestore package went wrong. More details can be given in the message parameter.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
case class TriplestoreInternalException(message: String, cause: Option[Throwable] = None)
    extends TriplestoreException(message, cause)

/**
 * Indicates that the triplestore returned an error message, or a response that could not be parsed.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
final case class TriplestoreResponseException(message: String, cause: Option[Throwable] = None)
    extends TriplestoreException(message, cause)

object TriplestoreResponseException {

  def apply(e: RdfError): TriplestoreResponseException =
    TriplestoreResponseException(e.toString, None)

  def apply(message: String): TriplestoreResponseException =
    TriplestoreResponseException(message, None)
}
