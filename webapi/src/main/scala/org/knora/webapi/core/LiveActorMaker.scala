/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.{Actor, Props}

/**
 * This trait is part of the cake pattern used in the creation of actors. This trait provides an implementation of the
 * makeActor method that creates actors as a child actor.
 */
trait LiveActorMaker extends ActorMaker {
  this: Actor =>

  def makeActor(props: Props, name: String) = context.actorOf(props, name)

}
