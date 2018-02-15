package org.knora.webapi.e2e.admin

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.knora.webapi.PerfSpec
import org.knora.webapi.e2e.admin.UsersPerformanceE2ESpec._

class UsersPerformanceE2ESpec extends PerfSpec(UsersPerformanceE2ESpec.config) {

    spec {
        http("Example index.html test")
                .get("/admin/users")
                .check(users.exists)
    }

}

object UsersPerformanceE2ESpec {

    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

    def users = jsonPath("users")

}
