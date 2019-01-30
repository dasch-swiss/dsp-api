/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

/*
package org.knora.webapi.store

/*
 * In this test case, our actor under test itself creates another actors. This
 * is why we need to instantiate the actor under test with TestProbeMake.
 * This allows us to get an ActorRef to this subactor by specifying his name.
 *
 * The naming of the test classes is usualy the class name appended by the
 * word 'spec' all in camel case.
 *
 * All test cases are subclasses of CoreSpec and need to provide parameters
 * providing the actor system name and config.
 *
 * to execute, type 'test' in sbt
 */

class StoreManagerSpec extends CoreSpec("StoreManagerTestSystem") with ImplicitSender {

    // here we start the actor under test with the TestProbeMaker trait
    val actorUnderTest = TestActorRef(Props(new StoreManager with TestProbeMaker), name = STORE_MANAGER_ACTOR_NAME)

    // here we get the ActorRef to a subactor with the name 'triplestoreManager' (ability provided by TestProbeMaker trait)
    val mockTriplestoreManagerActor = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(TRIPLESTORE_MANAGER_ACTOR_NAME, null)

    "The StoreManager, depending on the configuration, " must {
        "start the 'triplestoreManager' " in {
            mockTriplestoreManagerActor.isInstanceOf[TestProbe] should === (true)
        }
    }

}
*/