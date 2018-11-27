/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora

import sbt.{Def, _}
import Keys._

object Dependencies {

    lazy val sysProps = settingKey[String]("all system properties")
    lazy val sysEnvs = settingKey[String]("all system environment variables")

    lazy val gdbHomePath = settingKey[String]("Path to the GraphDB home directory")
    lazy val gdbLicensePath = settingKey[String]("Path to the GraphDB license")
    lazy val gdbImage = settingKey[String]("The GraphDB docker image")

    lazy val sipiVersion = settingKey[String]("The SIPI version for the docker image")
    lazy val akkaVersion = settingKey[String]("The Akka version")
    lazy val akkaHttpVersion = settingKey[String]("The AkkaHttp version")
    lazy val jenaVersion = settingKey[String]("The Jena library version")
    lazy val metricsVersion = settingKey[String]("The metrics library version")

    akkaVersion := "2.5.18"
    akkaHttpVersion := "10.1.5"
    jenaVersion := "3.4.0"
    metricsVersion := "4.0.1"

    object Compile {
        // akka
        val akkaActor              = Def.setting {"com.typesafe.akka"          %% "akka-actor"               % akkaVersion.value}
        val akkaAgent              = Def.setting {"com.typesafe.akka"          %% "akka-agent"               % akkaVersion.value}
        val akkaStream             = Def.setting {"com.typesafe.akka"          %% "akka-stream"              % akkaVersion.value}
        val akkaSlf4j              = Def.setting {"com.typesafe.akka"          %% "akka-slf4j"               % akkaVersion.value}

        // akka http
        val akkaHttp               = Def.setting {"com.typesafe.akka"          %% "akka-http"                % akkaHttpVersion.value}
        val akkaHttpXml            = Def.setting {"com.typesafe.akka"          %% "akka-http-xml"            % akkaHttpVersion.value}
        val akkaHttpSprayJson      = Def.setting {"com.typesafe.akka"          %% "akka-http-spray-json"     % akkaHttpVersion.value}
        val akkaHttpJacksonJava    = Def.setting {"com.typesafe.akka"          %% "akka-http-jackson"        % akkaHttpVersion.value}

        val typesafeConfig         = "com.typesafe"                             % "config"                   % "1.3.3"

        // testing

        //CORS support
        val akkaHttpCors           = "ch.megard"                               %% "akka-http-cors"           % "0.3.0"

        // jena
        val jenaLibs               = Def.setting {"org.apache.jena"             % "apache-jena-libs"         % jenaVersion.value exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")}
        val jenaText               = Def.setting {"org.apache.jena"             % "jena-text"                % jenaVersion.value exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")}

        // logging
        val commonsLogging         = "commons-logging"                          % "commons-logging"          % "1.2"
        val scalaLogging           = "com.typesafe.scala-logging"              %% "scala-logging"            % "3.8.0"
        val logbackClassic         = "ch.qos.logback"                           % "logback-classic"          % "1.2.3"

        // input validation
        val commonsValidator       = "commons-validator"                        % "commons-validator"        % "1.6" exclude("commons-logging", "commons-logging")

        // authentication
        val bcprov                 = "org.bouncycastle"                         % "bcprov-jdk15on"           % "1.59"
        val springSecurityCore     = "org.springframework.security"             % "spring-security-core"     % "4.2.5.RELEASE" exclude("commons-logging", "commons-logging") exclude("org.springframework", "spring-aop")
        val jwt                    = "io.igl"                                  %% "jwt"                      % "1.2.2" exclude("commons-codec", "commons-codec")

        // caching
        val ehcache                = "net.sf.ehcache"                           % "ehcache"                  % "2.10.3"

        // monitoring
        val kamonCore              = "io.kamon"                                %% "kamon-core"               % "1.1.3"
        val kamonAkka              = "io.kamon"                                %% "kamon-akka-2.5"           % "1.1.1"
        val kamonAkkaHttp          = "io.kamon"                                %% "kamon-akka-http-2.5"      % "1.1.0"
        val kamonPrometheus        = "io.kamon"                                %% "kamon-prometheus"         % "1.1.1"
        val kamonZipkin            = "io.kamon"                                %% "kamon-zipkin"             % "1.0.0"
        val kamonJaeger            = "io.kamon"                                %% "kamon-jaeger"             % "1.0.2"
        val aspectJWeaver          = "org.aspectj"                              % "aspectjweaver"            % "1.9.1"

        // other
        //"javax.transaction" % "transaction-api" % "1.1-rev-1",
        val commonsText            = "org.apache.commons"                       % "commons-text"             % "1.6"
        val commonsIo              = "commons-io"                               % "commons-io"               % "2.6"
        val commonsBeanUtil        = "commons-beanutils"                        % "commons-beanutils"        % "1.9.3" exclude("commons-logging", "commons-logging") // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
        val jodd                   = "org.jodd"                                 % "jodd"                     % "3.2.6"
        val jodaTime               = "joda-time"                                % "joda-time"                % "2.9.1"
        val jodaConvert            = "org.joda"                                 % "joda-convert"             % "1.8"
        val diff                   = "com.sksamuel.diff"                        % "diff"                     % "1.1.11"
        val xmlunitCore            = "org.xmlunit"                              % "xmlunit-core"             % "2.1.1"

        // other
        val rdf4jRuntime           = "org.eclipse.rdf4j"                        % "rdf4j-runtime"            % "2.3.2"
        val scallop                = "org.rogach"                              %% "scallop"                  % "2.0.5"
        val gwtServlet             = "com.google.gwt"                           % "gwt-servlet"              % "2.8.0"
        val saxonHE                = "net.sf.saxon"                             % "Saxon-HE"                 % "9.7.0-14"

        val scalaXml               = "org.scala-lang.modules"                  %% "scala-xml"                % "1.1.1"
        val scalaArm               = "com.jsuereth"                             % "scala-arm_2.12"           % "2.0"
        val scalaJava8Compat       = "org.scala-lang.modules"                   % "scala-java8-compat_2.12"  % "0.8.0"

        // provides akka jackson (json) support
        val akkaHttpCirce          = "de.heikoseeberger"                       %% "akka-http-circe"          % "1.21.0"
        val jacksonScala           = "com.fasterxml.jackson.module"            %% "jackson-module-scala"     % "2.9.4"

        val jsonldJava             = "com.github.jsonld-java"                   % "jsonld-java"              % "0.12.0"

        // swagger (api documentation)
        val swaggerAkkaHttp        = "com.github.swagger-akka-http"            %% "swagger-akka-http"        % "0.14.0"

        // Java EE modules which are deprecated in Java SE 9, 10 and will be removed in Java SE 11
        val jaxbApi                = "javax.xml.bind"                           % "jaxb-api"                 % "2.2.12"

        val icu4j                  = "com.ibm.icu"                              % "icu4j"                    % "62.1"


    }

    object SalsahTest {
        val akkaTestkit            = Def.setting {"com.typesafe.akka"            %% "akka-testkit"             % akkaVersion.value        % "test"}
        val akkaStreamTestkit      = Def.setting {"com.typesafe.akka"            %% "akka-stream-testkit"      % akkaVersion.value        % "test"}
        val akkaHttpTestkit        = Def.setting {"com.typesafe.akka"            %% "akka-http-testkit"        % akkaHttpVersion.value    % "test"}
        val scalaTest              = "org.scalatest"                             %% "scalatest"                % "3.0.4"                  % "test"

        // browser tests
        val selenium               = "org.seleniumhq.selenium"                    % "selenium-java"            % "3.4.0"                  % "test"
    }

    object WebapiTest {
        val akkaTestkit            = Def.setting {"com.typesafe.akka"            %% "akka-testkit"             % akkaVersion.value        % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"}
        val akkaStreamTestkit      = Def.setting {"com.typesafe.akka"            %% "akka-stream-testkit"      % akkaVersion.value        % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"}
        val akkaHttpTestkit        = Def.setting {"com.typesafe.akka"            %% "akka-http-testkit"        % akkaHttpVersion.value    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"}
        val scalaTest              = "org.scalatest"                             %% "scalatest"                % "3.0.4"                  % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingHighcharts      = "io.gatling.highcharts"                      % "gatling-charts-highcharts"% "2.3.1"                  % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingTestFramework   = "io.gatling"                                 % "gatling-test-framework"   % "2.3.1"                  % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

    }

    import Compile._

    val l = libraryDependencies

    val salsahLibraryDependencies = l ++= Seq[sbt.ModuleID](
        akkaActor.value,
        akkaAgent.value,
        akkaStream.value,
        akkaSlf4j.value,
        akkaHttp.value,
        akkaHttpXml.value,
        akkaHttpSprayJson.value,
        SalsahTest.akkaTestkit.value,
        SalsahTest.akkaHttpTestkit.value,
        SalsahTest.akkaStreamTestkit.value,
        SalsahTest.scalaTest,
        SalsahTest.selenium
    )

    val webapiLibraryDependencies = l ++= Seq[sbt.ModuleID](
        aspectJWeaver,
        akkaActor.value,
        akkaAgent.value,
        akkaHttp.value,
        akkaHttpCirce,
        akkaHttpCors,
        akkaHttpSprayJson.value,
        akkaHttpJacksonJava.value,
        WebapiTest.akkaHttpTestkit.value,
        akkaHttpXml.value,
        akkaSlf4j.value,
        akkaStream.value,
        WebapiTest.akkaStreamTestkit.value,
        WebapiTest.akkaTestkit.value,
        bcprov,
        commonsBeanUtil,
        commonsIo,
        commonsText,
        commonsValidator,
        diff,
        ehcache,
        WebapiTest.gatlingHighcharts,
        WebapiTest.gatlingTestFramework,
        gwtServlet,
        jacksonScala,
        jaxbApi,
        jsonldJava,
        jodd,
        jodaTime,
        jodaConvert,
        jenaLibs.value,
        jenaText.value,
        jwt,
        //library.kamonCore,
        //library.kamonAkka,
        //library.kamonAkkaHttp,
        //library.kamonPrometheus,
        //library.kamonZipkin,
        //library.kamonJaeger,
        logbackClassic,
        rdf4jRuntime,
        saxonHE,
        scalaArm,
        scalaJava8Compat,
        scalaLogging,
        WebapiTest.scalaTest,
        scalaXml,
        scallop,
        springSecurityCore,
        swaggerAkkaHttp,
        typesafeConfig,
        xmlunitCore,
        icu4j
    )


}