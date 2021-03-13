package co.ledger.lama.common.logging

import doobie.util.log.{ExecFailure, LogHandler, ProcessingFailure, Success}

import scala.concurrent.duration.FiniteDuration

trait DoobieLogHandler extends IOLogging {

  implicit val logHandler: LogHandler = LogHandler {
    case Success(query, arguments, execTime, processTime) =>
      logger.debug(
        s"""Successful Statement Execution for query:""" +
          displayDetails(query, arguments, execTime, Some(processTime), None)
      )

    case ProcessingFailure(query, arguments, execTime, processTime, error) =>
      logger.error(
        s"""Failed Resultset Processing for query:""" +
          displayDetails(query, arguments, execTime, Some(processTime), Some(error))
      )

    case ExecFailure(query, arguments, execTime, error) =>
      logger.error(
        s"""Failed Statement Execution for query:""" +
          displayDetails(query, arguments, execTime, None, Some(error))
      )

  }

  private def toMs(t: FiniteDuration) = t.toMillis.toString

  private def displayDetails(
      query: String,
      arguments: List[Any],
      execTime: FiniteDuration,
      processTime: Option[FiniteDuration],
      error: Option[Throwable]
  ) =
    s"""
          ${query.trim}
        arguments = ${arguments.mkString("[", ", ", "]")}
        elapsed   = ${toMs(execTime)} ms exec""" +
      processTime
        .map(time => s""" + ${toMs(time)} ms processing (${toMs(execTime + time)} ms total)""")
        .getOrElse("") +
      error
        .map(e => s""" (failed)
        failure   = ${e.getMessage}""")
        .getOrElse("")
}
