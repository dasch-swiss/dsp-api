/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import akka.event.LoggingAdapter
import org.apache.commons.lang3.{SerializationException, SerializationUtils}

/*

    How to use and extend these exceptions
    ======================================

    Two kinds of exceptions
    -----------------------

    1. Exceptions extending RequestRejectedException mean that the problem is the client's fault. They are just
       reported to the client.

    2. Exceptions extending InternalServerException mean that the problem is not the client's fault. They are
       reported to the client and are also logged (e.g. to the console). When thrown in an actor, they are also
       escalated to the actor's supervisor.

    Error-handling tools
    --------------------

    In an actor, have the receive method handle each different incoming message type by calling some private method that
    returns a Future. The receive method can then pass this object to ActorUtil.future2Message, which takes care of
    reporting exceptions as described above.

    If you extract data from a Map, and there's any chance some required data might be missing, wrap your Map in
    an ErrorHandlingMap, which takes some of the work out of checking for missing values and throwing helpful
    exceptions.

    Adding new exception classes
    ----------------------------

    Only the leaf nodes in the tree of exception classes can be case classes, because case classes can't be subclassed.

    All our exceptions should extend the trait KnoraException. This declares them Serializable, and ensures that they
    also extend Exception.

    The constructor of every exception should, at minimum, take a message: String argument, and pass it
    up the class hierarchy to the constructor of Exception. It will then appear in logs and in the string returned by
    the exception's toString method.

    Your exception's constructor can also take a nested exception argument representing the original cause of the
    error, which should also be passed up the class hierarchy to the constructor of Exception. Then you'll get nice
    chained stack traces in your logs. However, you need to ensure that the nested exception is serializable, otherwise
    your Actor won't be able to report it. Use ExceptionUtil.logAndWrapIfNotSerializable for this. See
    TriplestoreConnectionException for an example.

*/


/**
  * A trait implemented by all Knora exceptions, which must be serializable and extend [[java.lang.Exception]].
  */
trait KnoraException extends Serializable {
    this: Exception =>
    {}
}

/**
  * An abstract base class for exceptions indicating that something about a request made it impossible to fulfil (e.g.
  * it was malformed or referred to nonexistent data).
  *
  * @param msg a description of the error.
  * @param cause the cause of the error.
  */
abstract class RequestRejectedException(msg: String, cause: Throwable = null) extends Exception(msg, cause) with KnoraException

object RequestRejectedException {
    // So we can match instances of RequestRejectedException, even though it's an abstract class
    def unapply(e: RequestRejectedException): Option[RequestRejectedException] = Option(e)
}

/**
  * An exception indicating that the request parameters did not make sense.
  *
  * @param message a description of the error.
  */
case class BadRequestException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that a user has provided bad credentials.
  *
  * @param message a description of the error.
  */
case class BadCredentialsException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that a user has made a request for which the user lacks the necessary permission.
  *
  * @param message a description of the error.
  */
case class ForbiddenException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that the requested data was not found.
  *
  * @param message a description of the error.
  */
case class NotFoundException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that a requested update is not allowed because it would create a duplicate value.
  *
  * @param message a description of the error.
  */
case class DuplicateValueException(message: String = "Duplicate values are not permitted") extends RequestRejectedException(message)

/**
  * An exception indicating that a requested update is not allowed because it would violate an ontology constraint,
  * e.g. an `knora-base:objectClassConstraint` or an OWL cardinality restriction.
  *
  * @param message a description of the error.
  */
case class OntologyConstraintException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that a requested update is not allowed because another user has edited the
  * data that was to be updated.
  *
  * @param message a description of the error.
  */
case class EditConflictException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that the submitted standoff is not valid.
  *
  * @param message a description of the error.
  */
case class InvalidStandoffException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that an error occurred when converting standoff markup to or from another format.
  *
  * @param message a description of the error.
  */
case class StandoffConversionException(message: String) extends RequestRejectedException(message)

/**
  * An exception indicating that the Gravsearch query submitted to the API v2 search route was invalid.
  *
  * @param message a description of the error.
  */
case class GravsearchException(message: String) extends RequestRejectedException(message)

/**
  * An exception indication that the JSON-LD submitted to the API v2 was invalid.
  * @param msg a description of the error.
  * @param cause the cause for the error
  */
case class InvalidJsonLDException(msg: String, cause: Throwable = null) extends RequestRejectedException(msg, cause)


/**
  * An abstract class for exceptions indicating that something went wrong and it's not the client's fault.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
abstract class InternalServerException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with KnoraException

object InternalServerException {
    // So we can match instances of InternalServerException, even though it's an abstract class
    def unapply(e: InternalServerException): Option[InternalServerException] = Option(e)
}

/**
  * An exception indicating that during authentication something unexpected happened.
  *
  * @param message a description of the error.
  */
case class AuthenticationException(message: String = "Error during authentication. Please report this as a possible bug.", cause: Option[Throwable] = None) extends InternalServerException(message)

object AuthenticationException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): AuthenticationException =
        AuthenticationException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that data could not be converted from one format to another. This exception should not be thrown when
  * validating user input, but rather when processing input that has already been validated, or data that has been
  * loaded from the triplestore.
  *
  * @param message a description of the error.
  */
case class DataConversionException(message: String) extends InternalServerException(message)

/**
  * An exception indicating that during file upload there was an error.
  *
  * @param message a description of the error.
  */
case class FileUploadException(message: String = "Error during file upload. Please report this as a possible bug.") extends InternalServerException(message)

/**
  * An exception indicating that a requested update was not performed, although it was expected to succeed.
  * This probably indicates a bug.
  *
  * @param message a description of the error.
  */
case class UpdateNotPerformedException(message: String = "A requested update was not performed. Please report this as a possible bug.") extends InternalServerException(message)

/**
  * An abstract class for exceptions indicating that something went wrong with the triplestore.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
abstract class TriplestoreException(message: String, cause: Option[Throwable] = None) extends InternalServerException(message, cause)

/**
  * Indicates that the network connection to the triplestore failed.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class TriplestoreConnectionException(message: String, cause: Option[Throwable] = None) extends TriplestoreException(message, cause)

object TriplestoreConnectionException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): TriplestoreConnectionException =
        TriplestoreConnectionException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that we tried using a feature which is unsuported by the selected triplestore.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class TriplestoreUnsupportedFeatureException(message: String, cause: Option[Throwable] = None) extends TriplestoreException(message, cause)

object TriplestoreUnsupportedFeatureException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): TriplestoreUnsupportedFeatureException =
        TriplestoreUnsupportedFeatureException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that something inside the Triplestore package went wrong. More details can be given in the message parameter.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class TriplestoreInternalException(message: String, cause: Option[Throwable] = None) extends TriplestoreException(message, cause)

object TriplestoreInternalException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): TriplestoreInternalException =
        TriplestoreInternalException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates an internal server error in standoff-related processing.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class StandoffInternalException(message: String, cause: Option[Throwable] = None) extends InternalServerException(message, cause)

object StandoffInternalException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): StandoffInternalException =
        StandoffInternalException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that something happened that should be impossible.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class AssertionException(message: String, cause: Option[Throwable] = None) extends InternalServerException(message, cause)

object AssertionException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): AssertionException =
        AssertionException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}


/**
  * Indicates that the triplestore returned an error message, or a response that could not be parsed.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class TriplestoreResponseException(message: String, cause: Option[Throwable] = None) extends TriplestoreException(message, cause)

object TriplestoreResponseException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): TriplestoreResponseException =
        TriplestoreResponseException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that the triplestore returned inconsistent data.
  *
  * @param message a description of the error.
  */
case class InconsistentTriplestoreDataException(message: String, cause: Option[Throwable] = None) extends TriplestoreException(message, cause)

object InconsistentTriplestoreDataException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): InconsistentTriplestoreDataException =
        InconsistentTriplestoreDataException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that the API server generated invalid JSON in an API response.
  *
  * @param message a description of the error.
  * @param cause   the original exception representing the cause of the error, if any.
  */
case class InvalidApiJsonException(message: String, cause: Option[Throwable] = None) extends InternalServerException(message, cause)

object InvalidApiJsonException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): InvalidApiJsonException =
        InvalidApiJsonException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * Indicates that an application lock could not be acquired.
  *
  * @param message a description of the error.
  */
case class ApplicationLockException(message: String) extends InternalServerException(message)

/**
  * Indicates that an error occurred in transaction management.
  */
case class TransactionManagementException(message: String) extends InternalServerException(message)

/**
  * Indicates that an Akka actor received an unexpected message.
  *
  * @param message a description of the error.
  */
case class UnexpectedMessageException(message: String) extends InternalServerException(message)

/**
  * Indicates that an error occurred in the application's cache.
  *
  * @param message a description of the error.
  */
case class ApplicationCacheException(message: String) extends InternalServerException(message)

/**
  * Indicates that an error occurred during the generation of SPARQL query code.
  *
  * @param message a description of the error.
  */
case class SparqlGenerationException(message: String) extends InternalServerException(message)

/**
  * A generic [[InternalServerException]] for wrapping any non-serializable exception in a serializable form.
  */
case class WrapperException(e: Throwable) extends InternalServerException(e.toString)

/**
  * Indicates that an error occurred when trying to write a file to the disk.
  *
  * @param message a description of the error.
  */
case class FileWriteException(message: String) extends InternalServerException(message)

/**
  * Indicates that a request attempted to use a feature that has not yet been implemented.
  *
  * @param message a description of the error.
  */
case class NotImplementedException(message: String) extends InternalServerException(message)


/**
  * Indicates that an error occurred with Sipi not relating to the user's request (it is not the user's fault).
  *
  * @param message a description of the error.
  */
case class SipiException(message: String, cause: Option[Throwable] = None) extends InternalServerException(message, cause)

object SipiException {
    def apply(message: String, e: Throwable, log: LoggingAdapter): SipiException =
        SipiException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
  * An abstract base class for exceptions indicating that something about a configuration made it impossible to start.
  *
  * @param message a description of the error.
  */
abstract class ApplicationConfigurationException(message: String) extends Exception(message) with KnoraException

object ApplicationConfigurationException {
    // So we can match instances of ApplicationConfigurationException, even though it's an abstract class
    def unapply(e: ApplicationConfigurationException): Option[ApplicationConfigurationException] = Option(e)
}

/**
  * Indicates that an unsupported triplestore was selected in the configuration.
  *
  * @param message a description of the error.
  */
case class UnsuportedTriplestoreException(message: String) extends ApplicationConfigurationException(message)

/**
  * Indicates that the HTTP configuration is incorrect.
  *
  * @param message a description of the error.
  */
case class HttpConfigurationException(message: String) extends ApplicationConfigurationException(message)


/**
  * Helper functions for error handling.
  */
object ExceptionUtil {
    /**
      * Checks whether an exception is serializable.
      *
      * @param e the exception to be checked.
      * @return `true` if the exception is serializable, otherwise `false`.
      */
    def isSerializable(e: Throwable): Boolean = {
        try {
            SerializationUtils.serialize(e)
            true
        } catch {
            case serEx: SerializationException => false
        }
    }

    /**
      * Checks whether an exception is serializable. If it is serializable, it is returned as-is. If not,
      * the exception is logged with its stack trace, and a string representation of it is returned in a
      * [[WrapperException]].
      *
      * @param e the exception to be checked.
      * @return the same exception, or a [[WrapperException]].
      */
    def logAndWrapIfNotSerializable(e: Throwable, log: LoggingAdapter): Throwable = {
        if (isSerializable(e)) {
            e
        } else {
            log.error(e, e.toString)
            WrapperException(e)
        }
    }
}