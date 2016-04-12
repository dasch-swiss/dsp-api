/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import akka.actor.{Actor, ActorLogging, Props}
import akka.testkit.TestProbe

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
        context.actorOf(Props(new Actor {
            def receive = {
                case msg => {
                    probe.ref forward msg
                }
            }
        }), name)
    }
}
