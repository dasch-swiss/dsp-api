/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import zio.*
import zio.json.*
import zio.json.ast.Json

import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.EdECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import scala.util.Try

import org.knora.webapi.config.ServiceIdentityConfig
import org.knora.webapi.slice.common.domain.ServiceName

final case class TrustedServiceKey(name: ServiceName, kid: String, publicKey: PublicKey)

/**
 * The export services' public keys this server trusts, by `kid`. Empty means the service identity is off.
 * Several keys may share a name, so a service can rotate its key with an overlap; each `kid` is unique.
 */
final case class TrustedServiceKeys private (byKid: Map[String, TrustedServiceKey]) {
  def isEmpty: Boolean                            = byKid.isEmpty
  def get(kid: String): Option[TrustedServiceKey] = byKid.get(kid)
}

object TrustedServiceKeys {

  val empty: TrustedServiceKeys = TrustedServiceKeys(Map.empty)

  private val Setting = "app.service-identity.trusted-keys"

  private final case class TrustedKeyDto(name: String, kid: String, publicKey: String)
  private object TrustedKeyDto {
    given JsonDecoder[TrustedKeyDto] = DeriveJsonDecoder.gen[TrustedKeyDto]
  }

  val layer: URLayer[ServiceIdentityConfig, TrustedServiceKeys] = ZLayer.fromZIO(
    ZIO
      .serviceWithZIO[ServiceIdentityConfig](config =>
        ZIO.fromEither(parse(config.trustedKeys)).orDieWith(new IllegalArgumentException(_)),
      )
      .tap(logLoaded),
  )

  /**
   * Parses the setting's JSON array. A blank value means no keys, like `[]`, because a declared but unset
   * environment variable renders as an empty string. Error messages name the entry index and field, never the key.
   */
  def parse(raw: String): Either[String, TrustedServiceKeys] =
    if (raw.isBlank) Right(empty)
    else
      for {
        entries <- parseArray(raw)
        keys    <- collectAll(entries.zipWithIndex.map(toKey))
        _       <- checkUniqueKids(keys)
      } yield TrustedServiceKeys(keys.map(k => k.kid -> k).toMap)

  private def parseArray(raw: String): Either[String, List[Json]] =
    raw.fromJson[Json] match {
      case Right(Json.Arr(entries)) => Right(entries.toList)
      case Right(_)                 => Left(s"$Setting must be a JSON array")
      case Left(_)                  => Left(s"$Setting is not valid JSON")
    }

  private def toKey(entry: Json, index: Int): Either[String, TrustedServiceKey] = {
    def invalid(field: String, problem: String) = s"$Setting[$index].$field $problem"
    for {
      dto <- entry
               .as[TrustedKeyDto]
               .left
               .map(_ => s"$Setting[$index] must be an object with the string fields name, kid and publicKey")
      name <- ServiceName
                .from(dto.name)
                .left
                .map(_ => invalid("name", "must be non-empty lower-case letters, digits and hyphens"))
      kid <- Either.cond(!dto.kid.isBlank, dto.kid, invalid("kid", "must not be blank"))
      key <- parsePublicKey(dto.publicKey)
               .toRight(invalid("publicKey", "is not the base64 SubjectPublicKeyInfo of an Ed25519 public key"))
    } yield TrustedServiceKey(name, kid, key)
  }

  private def collectAll[A](results: List[Either[String, A]]): Either[String, List[A]] =
    results.partitionMap(identity) match {
      case (Nil, values) => Right(values)
      case (errors, _)   => Left(errors.mkString("; "))
    }

  private def checkUniqueKids(keys: List[TrustedServiceKey]): Either[String, Unit] = {
    val duplicates = keys.zipWithIndex.collect {
      case (key, index) if keys.take(index).exists(_.kid == key.kid) => s"$Setting[$index].kid is a duplicate"
    }
    Either.cond(duplicates.isEmpty, (), duplicates.mkString("; "))
  }

  // Accepts what `openssl pkey -pubout` prints: PEM armour and line breaks are optional.
  private def parsePublicKey(text: String): Option[PublicKey] = {
    val base64 = text
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replace("-----END PUBLIC KEY-----", "")
      .filterNot(_.isWhitespace)
    Try(
      KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder.decode(base64))),
    ).toOption.collect { case key: EdECPublicKey if key.getParams.getName.equalsIgnoreCase("Ed25519") => key }
  }

  private def logLoaded(keys: TrustedServiceKeys): UIO[Unit] = {
    val listed = keys.byKid.values.toList.sortBy(_.kid).map(k => s"${k.name} (kid ${k.kid})")
    ZIO.logInfo(
      s"Service identity: ${listed.size} trusted key(s)" + (if (listed.isEmpty) "" else listed.mkString(": ", ", ", "")),
    )
  }
}
