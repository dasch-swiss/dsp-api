/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import sbt.Keys._
import sbt.{Def, _}

object Dependencies {

    lazy val sysProps = settingKey[String]("all system properties")
    lazy val sysEnvs = settingKey[String]("all system environment variables")

    lazy val gdbHomePath = settingKey[String]("path to the GraphDB home directory")
    lazy val gdbLicensePath = settingKey[String]("path to the GraphDB license")
    lazy val gdbSEImage = settingKey[String]("the GraphDB-SE docker image")
    lazy val gdbFreeImage = settingKey[String]("the GraphDB-Free docker image")

    lazy val sipiImage = settingKey[String]("the SIPI docker image")
    lazy val akkaVersion = settingKey[String]("the Akka version")
    lazy val akkaHttpVersion = settingKey[String]("the AkkaHttp version")
    lazy val jenaVersion = settingKey[String]("the Jena library version")
    lazy val metricsVersion = settingKey[String]("the metrics library version")

    lazy val knoraJenaFusekiImage = SettingKey[String]("the Knora specific Jena Fuseki Image")
    lazy val knoraSipiImage = SettingKey[String]("the Knora specific Sipi Image")
    lazy val knoraWebapiImage = SettingKey[String]("the Knora webapi Image")
    lazy val knoraSalsah1Image = SettingKey[String]("the Knora Salsah1 Image")

    val Versions = Seq(
        scalaVersion := "2.13.8",
        akkaVersion := "2.6.17",
        akkaHttpVersion := "10.2.4",
        jenaVersion := "4.4.0",
        metricsVersion := "4.0.1",
        sipiImage := "daschswiss/sipi:v3.3.1",
        gdbSEImage := "daschswiss/graphdb:9.0.0-se",
        gdbFreeImage := "daschswiss/graphdb:9.0.0-free"
    )

    // the user can change the default 'graphdb-se' value by creating an environment variable containing 'graphdb-free'
    // e.g., in '$ export KNORA_GDB_TYPE=graphdb-free' in the terminal before launching sbt.
    lazy val gdbTypeString: String = sys.env.getOrElse("KNORA_GDB_TYPE", "graphdb-se")

    object Compile {
        // akka
        val akkaActor              = Def.setting {"com.typesafe.akka"          %% "akka-actor"               % akkaVersion.value}
        val akkaStream             = Def.setting {"com.typesafe.akka"          %% "akka-stream"              % akkaVersion.value}
        val akkaSlf4j              = Def.setting {"com.typesafe.akka"          %% "akka-slf4j"               % akkaVersion.value}

        // akka http
        val akkaHttp               = Def.setting {"com.typesafe.akka"          %% "akka-http"                % akkaHttpVersion.value}
        val akkaHttpXml            = Def.setting {"com.typesafe.akka"          %% "akka-http-xml"            % akkaHttpVersion.value}
        val akkaHttpSprayJson      = Def.setting {"com.typesafe.akka"          %% "akka-http-spray-json"     % akkaHttpVersion.value}
        val akkaHttpJacksonJava    = Def.setting {"com.typesafe.akka"          %% "akka-http-jackson"        % akkaHttpVersion.value}

        val typesafeConfig         = "com.typesafe"                             % "config"                   % "1.3.3"

        //CORS support
        val akkaHttpCors           = "ch.megard"                               %% "akka-http-cors"           % "1.0.0"

        // jena
        val jenaLibs               = Def.setting {"org.apache.jena"             % "apache-jena-libs"         % jenaVersion.value exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")}
        val jenaText               = Def.setting {"org.apache.jena"             % "jena-text"                % jenaVersion.value exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")}

        // logging
        val commonsLogging         = "commons-logging"                          % "commons-logging"          % "1.2"
        val scalaLogging           = "com.typesafe.scala-logging"              %% "scala-logging"            % "3.8.0"
        val logbackClassic         = "ch.qos.logback"                           % "logback-classic"          % "1.2.3"

        // Metrics
        val kamonCore              = "io.kamon"                                %% "kamon-core"               % "2.0.0-RC1"
        val kamonScalaFuture       = "io.kamon"                                %% "kamon-scala-future"       % "2.0.0-RC1"
        // val kamonAkkaHttpd         = "io.kamon"                                %% "kamon-akka-http"          % "2.0.0-RC3"
        val kamonPrometheus        = "io.kamon"                                %% "kamon-prometheus"         % "2.0.0-RC1"
        val kamonLogback           = "io.kamon"                                %% "kamon-logback"            % "2.0.0-RC1"
        val aspectJWeaver          = "org.aspectj"                              % "aspectjweaver"            % "1.9.4"

        // input validation
        val commonsValidator       = "commons-validator"                        % "commons-validator"        % "1.6" exclude("commons-logging", "commons-logging")

        // authentication
        val bcprov                 = "org.bouncycastle"                         % "bcprov-jdk15on"           % "1.59"
        val springSecurityCore     = "org.springframework.security"             % "spring-security-core"     % "5.1.5.RELEASE" exclude("commons-logging", "commons-logging") exclude("org.springframework", "spring-aop")
        val jwtSprayJson           = "com.pauldijou"                           %% "jwt-spray-json"           % "0.19.0"

        // caching
        val ehcache                = "net.sf.ehcache"                           % "ehcache"                  % "2.10.3"
        val jedis                  = "redis.clients"                            % "jedis"                    % "3.1.0-m4"
        // serialization
        val chill                  = "com.twitter"                             %% "chill"                    % "0.9.3"

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
        val rdf4jRuntime           = "org.eclipse.rdf4j"                        % "rdf4j-runtime"            % "3.0.0"
        val scallop                = "org.rogach"                              %% "scallop"                  % "3.2.0"
        val gwtServlet             = "com.google.gwt"                           % "gwt-servlet"              % "2.8.0"
        val saxonHE                = "net.sf.saxon"                             % "Saxon-HE"                 % "9.9.0-2"

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

        val apacheHttpClient       = "org.apache.httpcomponents"                % "httpclient"               % "4.5.6" exclude("commons-logging", "commons-logging")
    }

    object SalsahTest {
        val akkaTestkit            = Def.setting {"com.typesafe.akka"            %% "akka-testkit"             % akkaVersion.value        % "test"}
        val akkaStreamTestkit      = Def.setting {"com.typesafe.akka"            %% "akka-stream-testkit"      % akkaVersion.value        % "test"}
        val akkaHttpTestkit        = Def.setting {"com.typesafe.akka"            %% "akka-http-testkit"        % akkaHttpVersion.value    % "test"}
        val scalaTest              = "org.scalatest"                             %% "scalatest"                % "3.1.2"                  % "test"

        // browser tests
        val selenium               = "org.seleniumhq.selenium"                    % "selenium-java"            % "3.4.0"                  % "test"
    }

    object WebapiTest {
        val akkaTestkit            = Def.setting {"com.typesafe.akka"            %% "akka-testkit"             % akkaVersion.value        % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"}
        val akkaStreamTestkit      = Def.setting {"com.typesafe.akka"            %% "akka-stream-testkit"      % akkaVersion.value        % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"}
        val akkaHttpTestkit        = Def.setting {"com.typesafe.akka"            %% "akka-http-testkit"        % akkaHttpVersion.value    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"}
        val scalaTest              = "org.scalatest"                             %% "scalatest"                % "3.1.2"                  % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingHighcharts      = "io.gatling.highcharts"                      % "gatling-charts-highcharts"% "2.3.1"                  % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingTestFramework   = "io.gatling"                                 % "gatling-test-framework"   % "2.3.1"                  % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val testcontainers         = "org.testcontainers"                         % "testcontainers"           % "1.14.3"                 % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
    }

    object TestBinaries {
        val akkaTestkit            = Def.setting {"com.typesafe.akka"            %% "akka-testkit"             % akkaVersion.value}
        val akkaStreamTestkit      = Def.setting {"com.typesafe.akka"            %% "akka-stream-testkit"      % akkaVersion.value}
        val akkaHttpTestkit        = Def.setting {"com.typesafe.akka"            %% "akka-http-testkit"        % akkaHttpVersion.value}
        val scalaTest              = "org.scalatest"                             %% "scalatest"                % "3.1.2"
        val gatlingHighcharts      = "io.gatling.highcharts"                      % "gatling-charts-highcharts"% "2.3.1"
        val gatlingTestFramework   = "io.gatling"                                 % "gatling-test-framework"   % "2.3.1"
    }

    import Compile._

    val l = libraryDependencies

    val salsahLibraryDependencies = l ++= Seq[sbt.ModuleID](
        akkaActor.value,
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
        akkaActor.value,
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
        apacheHttpClient,
        bcprov,
        chill,
        commonsBeanUtil,
        commonsIo,
        commonsText,
        commonsValidator,
        diff,
        ehcache,
        WebapiTest.gatlingHighcharts,
        WebapiTest.gatlingTestFramework,
        gwtServlet,
        icu4j,
        jacksonScala,
        jaxbApi,
        jsonldJava,
        jodd,
        jodaTime,
        jodaConvert,
        jedis,
        jenaLibs.value,
        jenaText.value,
        jwtSprayJson,
        kamonCore,
        kamonLogback,
        kamonPrometheus,
        kamonScalaFuture,
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
        WebapiTest.testcontainers,
        typesafeConfig,
        xmlunitCore
    )

    val upgradeLibraryDependencies = l ++= Seq[sbt.ModuleID](
        rdf4jRuntime,
        SalsahTest.scalaTest,
        scallop
    )

    val webapiTestAndITLibraryDependencies = l ++= Seq[sbt.ModuleID](
        //TestBinaries.akkaTestkit,
        //TestBinaries.akkaStreamTestkit,
        //TestBinaries.akkaHttpTestkit,
        TestBinaries.gatlingHighcharts,
        TestBinaries.gatlingTestFramework,
        TestBinaries.scalaTest
    )


}