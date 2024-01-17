package org.knora.webapi.slice.common

import zio.prelude.Validation

trait WithSmartConstructors[T] {

  def from(str: String): Either[String, T]

  final def unsafeFrom(str: String): T =
    from(str).fold(e => throw new IllegalArgumentException(e), identity)

  final def validationFrom(str: String): Validation[String, T] =
    Validation.fromEither(from(str))
}
