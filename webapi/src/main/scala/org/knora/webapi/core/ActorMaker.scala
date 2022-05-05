/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

/**
 * This trait is part of the cake pattern used in the creation of actors. Here we only define the method, and with
 * the forward declaration we make sure that it can only be attached to an actor.
 */
trait ActorMaker {
  this: Actor =>

  def makeActor(props: Props, name: String): ActorRef

}
