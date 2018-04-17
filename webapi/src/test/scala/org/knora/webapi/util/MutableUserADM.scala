package org.knora.webapi.util

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

/**
  * Holds an optional, mutable IRI for use in tests.
  */
class MutableUserADM {
    private var maybeUserProfile: Option[UserADM] = None

    /**
      * Stores the user's profile.
      * @param userProfile the user's profile to be stored.
      */
    def set(userProfile: UserADM): Unit = {
        maybeUserProfile = Some(userProfile)
    }

    /**
      * Removes any stored IRI.
      */
    def unset(): Unit = {
        maybeUserProfile = None
    }

    /**
      * Gets the stored IRI, or throws an exception if the IRI is not set.
      * @return the stored IRI.
      */
    def get: UserADM = {
        maybeUserProfile.getOrElse(throw TestUserProfileException("This test could not be run because a previous test failed"))
    }
}

/**
  * Thrown if a stored IRI was needed but was not set.
  */
case class TestUserProfileException(message: String) extends Exception(message)
