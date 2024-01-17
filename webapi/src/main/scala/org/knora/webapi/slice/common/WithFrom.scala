package org.knora.webapi.slice.common

trait WithFrom[I, A] {

  def from(in: I): Either[String, A]

  final def unsafeFrom(in: I): A =
    from(in).fold(e => throw new IllegalArgumentException(e), identity)

}

object WithFrom {
  type WithFromString[A] = WithFrom[String, A]
}
