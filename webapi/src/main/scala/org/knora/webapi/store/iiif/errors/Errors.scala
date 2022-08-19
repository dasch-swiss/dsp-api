package org.knora.webapi.store.iiif.errors

import com.typesafe.scalalogging.Logger

import dsp.errors.ExceptionUtil
import dsp.errors.InternalServerException

/**
 * Indicates that an error occurred with Sipi not relating to the user's request (it is not the user's fault).
 *
 * @param message a description of the error.
 */
final case class SipiException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

object SipiException {
  def apply(message: String, e: Throwable, log: Logger): SipiException =
    SipiException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))

  def apply(message: String, e: Throwable): SipiException =
    SipiException(message, Some(e))
}
