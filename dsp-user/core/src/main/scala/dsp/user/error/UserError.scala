package dsp.user.error

import dsp.user.domain.Username
import dsp.user.domain.Email

object UserError {
  case class UserAlreadyExists(usernameOption: Option[Username], emailOption: Option[Email]) extends Throwable

}
