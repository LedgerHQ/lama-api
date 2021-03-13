import sbt.librarymanagement.{DependencyBuilders, LibraryManagementSyntax, ModuleID}

object Dependencies extends DependencyBuilders with LibraryManagementSyntax {

  val http4sVersion = "0.21.20"
  val http4s: Seq[ModuleID] = Seq(
    "org.http4s" %% "http4s-blaze-server"       % http4sVersion,
    "org.http4s" %% "http4s-blaze-client"       % http4sVersion,
    "org.http4s" %% "http4s-circe"              % http4sVersion,
    "org.http4s" %% "http4s-dsl"                % http4sVersion,
    "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion
  )

  val circeVersion = "0.13.0"
  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"           % circeVersion,
    "io.circe" %% "circe-parser"         % circeVersion,
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion
  )

  val H2Version     = "1.4.200"
  val flywayVersion = "7.7.0"
  val doobieVersion = "0.10.0"
  val postgres: Seq[ModuleID] = Seq(
    "com.h2database" % "h2"              % H2Version,
    "org.flywaydb"   % "flyway-core"     % flywayVersion,
    "org.tpolecat"  %% "doobie-core"     % doobieVersion,
    "org.tpolecat"  %% "doobie-postgres" % doobieVersion,
    "org.tpolecat"  %% "doobie-hikari"   % doobieVersion,
    "org.tpolecat"  %% "doobie-h2"       % doobieVersion
  )

  val pureconfigVersion   = "0.14.1"
  val logbackVersion      = "1.2.3"
  val fs2Version          = "2.5.3"
  val fs2GrpcVersion      = "0.9.0"
  val protobufJava        = "3.15.6"
  val scalaLoggingVersion = "3.9.2"

  val utilities: Seq[ModuleID] = Seq(
    "com.typesafe.scala-logging" %% "scala-logging"   % scalaLoggingVersion,
    "co.fs2"                     %% "fs2-core"        % fs2Version,
    "org.lyranthe.fs2-grpc"      %% "java-runtime"    % fs2GrpcVersion,
    "ch.qos.logback"              % "logback-classic" % logbackVersion,
    "com.github.pureconfig"      %% "pureconfig"      % pureconfigVersion,
    "com.github.pureconfig"      %% "pureconfig-cats" % pureconfigVersion
  )

  val fs2RabbitVersion = "3.0.1"
  val rabbit: Seq[ModuleID] = Seq(
    "dev.profunktor" %% "fs2-rabbit" % fs2RabbitVersion
  )

  val scalaRedisVersion = "3.30"
  val redis: Seq[ModuleID] = Seq(
    "net.debasishg" %% "redisclient" % scalaRedisVersion
  )

  val scalaTestVersion     = "3.2.6"
  val scalaTestPlusVersion = "3.2.2.0"
  val scalaCheckVersion    = "1.15.3"
  val otjPgEmbeddedVersion = "0.13.3"
  val embeddedRedisVersion = "0.7.3"
  val test: Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"        % scalaTestVersion     % "it, test",
    "org.scalacheck"          %% "scalacheck"       % scalaCheckVersion    % "it, test",
    "org.scalatestplus"       %% "scalacheck-1-14"  % scalaTestPlusVersion % "it, test",
    "org.tpolecat"            %% "doobie-scalatest" % doobieVersion        % "it, test",
    "com.opentable.components" % "otj-pg-embedded"  % otjPgEmbeddedVersion % Test,
    "it.ozimov"                % "embedded-redis"   % embeddedRedisVersion % Test
  )

  // https://scalapb.github.io/docs/faq/#i-am-getting-import-was-not-found-or-had-errors
  val commonProtos: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime"   % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "io.grpc"               % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
    "com.google.protobuf"   % "protobuf-java"     % protobufJava
  )

  val bouncyCastleCryptoVersion = "1.68"
  val crypto: Seq[ModuleID] = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleCryptoVersion
  )

  val lamaCommon: Seq[ModuleID] = circe ++ rabbit ++ utilities ++ postgres ++ http4s

  val btcCommon: Seq[ModuleID]      = lamaCommon
  val btcWorker: Seq[ModuleID]      = btcCommon
  val btcInterpreter: Seq[ModuleID] = btcCommon ++ crypto
  val btcApi: Seq[ModuleID]         = btcCommon
  val btcTransactor: Seq[ModuleID]  = btcCommon

  val accountManager: Seq[ModuleID] = lamaCommon ++ redis
}
