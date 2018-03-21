package com.egc.pricing.loadtest

import com.typesafe.config._

import grizzled.slf4j.Logger
import com.egc.pricing.loadtest.util.TestUtils
import java.net.InetAddress

/**
 * TODO... Scaladoc
 */
object HttpLoadTester {

  val logger = Logger("HttpLoadTester")

  def main(args: Array[String]) = {
    try {
      logger.info("Printing Test parameters: **** ")
      logger.info(s"ServerHost = ${TestUtils.host} [${InetAddress.getLocalHost.getHostName}]")
      logger.info(s"ServerPort = ${TestUtils.port}")
      logger.info(s"IsSanityCheck = ${TestUtils.isCheck}")
      logger.info(s"TestData = ${TestUtils.testDataFilePath}")
      logger.info(s"Logfile MarketData Message Count = ${TestUtils.marketDataRequestsJSON.size}")
      logger.info(s"Logfile Price Message Count = ${TestUtils.priceRequestsJSON.size}")
      logger.info(s"Total MarketDataRequest Workers = ${TestUtils.noOfMarketDataRequestWorkers}")
      logger.info(s"Total PricingRequest Workers = ${TestUtils.noOfPricingRequestWorkers}")
      TestStarter.startTesting
    } catch {
      case t: Throwable =>
        logger.error("Unexpected Error Occurred")
        t.printStackTrace()
        logger.error("Exiting!!! *** ")
        sys.exit(-1)
    }
  }

  private def getOrElseServerDetails(config: Config): (String, Int) = {
    val x: Option[(String, Int)] = for {
      host <- Option(config.getString("host"))
      port <- Option(config.getInt("port"))
    } yield (host, port)

    x match {
      case Some(y: (String, Int)) => (y._1, y._2)
      case None => ("localhost", 9000)
    }
  }

  private def getOrElseWebSocketEndpointNames(config: Config): (String, String) = {
    val x: Option[(String, String)] = for {
      price <- Option(config.getString("wsPriceRequestMethod"))
      marketData <- Option(config.getString("wsMarketDataRequestMethod"))
    } yield (price, marketData)

    x match {
      case Some(y: (String, String)) => (y._1, y._2)
      case None => ("price", "marketDataRequest")
    }
  }

  private def hasOrElseSanityCheck(config: Config): Boolean = {
    val check = for {
      isCheck <- Option(config.getBoolean("isSanityCheck"))
    } yield isCheck

    check match {
      case Some(x) => true
      case None => false
    }
  }

  private def getOrElseMDRAndPricingWorkerCount(config: Config): (Int, Int) = {
    val x: Option[(Int, Int)] = for {
      host <- Option(config.getInt("pricingWorkerCount"))
      port <- Option(config.getInt("marketDataWorkerCount"))
    } yield (host, port)

    x match {
      case Some(y: (Int, Int)) => (y._1, y._2)
      case None => (64, 64)
    }
  }
}