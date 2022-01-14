/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.{Actor, ActorLogging, Props}
import akka.testkit.TestProbe
import org.knora.webapi.core.ActorMaker

/**
 * This trait is part of the cake pattern used in the creation of actors. This trait provides an implementation of the
 * makeActor method that creates an actor as a [[TestProbe]], which can than be used in testing.
 */
trait TestProbeMaker extends ActorMaker {
  this: Actor with ActorLogging =>

  lazy val probes = scala.collection.mutable.Map[String, TestProbe]()

  def makeActor(props: Props, name: String) = {
    val probe = new TestProbe(context.system)
    probes(name) = probe
    log.debug(s"created test-probe named: $name")
    context.actorOf(
      Props(new Actor {
        def receive = {
          case msg => {
            probe.ref forward msg
          }
        }
      }),
      name
    )
  }
}
