package org.knora.webapi.util

import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1

/**
  * Holds an optional, mutable IRI for use in tests.
  */
class MutableUserProfileV1 {
    private var maybeUserProfile: Option[UserProfileV1] = None

    /**
      * Stores the user's profile.
      * @param userProfileV1 the user's profile to be stored.
      */
    def set(userProfileV1: UserProfileV1): Unit = {
        maybeUserProfile = Some(userProfileV1)
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
    def get: UserProfileV1 = {
        maybeUserProfile.getOrElse(throw TestUserProfileException("This test could not be run because a previous test failed"))
    }
}

/**
  * Thrown if a stored IRI was needed but was not set.
  */
case class TestUserProfileException(message: String) extends Exception(message)
