package org.knora.webapi.slice.admin.domain.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import zio.ZIO
import zio.ZLayer

import java.security.MessageDigest
import java.security.SecureRandom

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.PasswordStrength

final case class PasswordService(passwordStrength: PasswordStrength) {
  // BCryptPasswordEncoder is the default password encoder and used for new passwords
  private val bCryptEncoder = new BCryptPasswordEncoder(passwordStrength.value, new SecureRandom())

  // The other encoders are only used for legacy passwords, which haven't been rehashed yet
  private val sCryptEncoder = new SCryptPasswordEncoder(16384, 8, 1, 32, 64)
  private val sha1Encoder   = MessageDigest.getInstance("SHA-1")

  def hashPassword(password: Password): PasswordHash =
    PasswordHash.unsafeFrom(bCryptEncoder.encode(password.value))

  def matches(password: Password, hashedPassword: PasswordHash): Boolean =
    matchesStr(password.value, hashedPassword.value)

  def matchesStr(rawPassword: String, hashedPassword: String): Boolean =
    // check the magic bytes at the beginning of the hash for which type of hash we have
    hashedPassword match {
      case s if s.startsWith("$e0801$") =>
        sCryptEncoder.matches(rawPassword, hashedPassword)
      case s if s.startsWith("$2a$") =>
        bCryptEncoder.matches(rawPassword, hashedPassword)
      case _ =>
        sha1Encoder.digest(rawPassword.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
    }
}
object PasswordService {

  val layer = ZLayer.fromZIO(
    ZIO
      .serviceWith[AppConfig](_.bcryptPasswordStrength)
      .map(PasswordStrength.unsafeFrom)
      .map(PasswordService.apply)
  )
}
