package com.egc.pricing.loadtest

import com.egc.ost.pricing.Logging.LogFileReader
import grizzled.slf4j.Logger


trait LogFileReaderHelper {

  val logFileReaderHelperLogger = Logger(classOf[LogFileReaderHelper])
}
object LogFileReaderHelper {

  def apply(logFilePath: String): LogFileReader = {
    new LogFileReader(logFilePath)
  }
}