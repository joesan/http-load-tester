package com.egc.pricing.loadtest.actors

import akka.actor.Actor
import grizzled.slf4j.Logger
import com.egc.pricing.loadtest.util.TestUtils
import scala.collection.mutable.ListBuffer
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import com.egc.pricing.loadtest.TestStarter


class MasterActor extends Actor {

  type StringListBufferAlias = ListBuffer[String]

  val PRICE_WORKER_SHUTDOWN = "priceWorkerShutdown"
  val MARKET_DATA_WORKER_SHUTDOWN = "marketDataWorkerShutdown"

  val masterActorLogger = Logger(classOf[MasterActor])

  var priceRequestWorkerCounter = 0
  var marketDataRequestWorkerCounter = 0

  val strPriceBuilder = new ListBuffer[String]
  val strMDBuilder = new ListBuffer[String]

  val marketDataRequestsSize = TestUtils.marketDataRequestsJSON.size
  val pricingRequestsSize = TestUtils.priceRequestsJSON.size

  val totalPricingRequestWorkers = TestUtils.noOfPricingRequestWorkers
  val totalMarketDataRequestWorkers = TestUtils.noOfMarketDataRequestWorkers

  var loopCount = 1

  var allFailedPricingRequests = ListBuffer.empty[String]
  var allFailedMarketDataRequests = ListBuffer.empty[String]

  def receive = {
      case x: (String, StringListBufferAlias) if(x._2.size >= 0) => {
      if (x._1 == PRICE_WORKER_SHUTDOWN) {
        priceRequestWorkerCounter = priceRequestWorkerCounter + 1
        allFailedPricingRequests ++= x._2
        if (priceRequestWorkerCounter == totalPricingRequestWorkers && loopCount == TestUtils.repeatTestCount) {
          val totalPricingRequests = if (TestUtils.repeatTestCount == 0) pricingRequestsSize else loopCount * pricingRequestsSize
          strPriceBuilder.append("****************** Pricing Requests Statistics ******************")
          strPriceBuilder.append("Server = " + TestUtils.host + " [" + InetAddress.getLocalHost.getHostName + "]")
          strPriceBuilder.append("Port = " + TestUtils.port + " [" + InetAddress.getLocalHost.getHostName + "]")
          strPriceBuilder.append("Is SanityCheck enabled = " + TestUtils.isCheck)
          strPriceBuilder.append("Total Pricing WebSocket Connections = " + TestUtils.noOfPricingRequestWorkers)
          strPriceBuilder.append("Total Pricing Requests Processed = " + totalPricingRequests)
          val totalTime = (System.currentTimeMillis - TestStarter.allPricingRequestsStartTime) / 1000
          strPriceBuilder.append("Total time taken to process " + totalPricingRequests + " Pricing Requests (in seconds) = " + totalTime)
          strPriceBuilder.append("The pricing server did " + totalPricingRequests / totalTime + " pricing requests in one second")
          val distinctFailedPricingRequests = allFailedPricingRequests.distinct.toList.size
          strPriceBuilder.append("Total Failed Pricing Requests " + distinctFailedPricingRequests)
          if (distinctFailedPricingRequests > 0)  {
            strPriceBuilder.append("Printing the UUID's for the failed Pricing Requests:")
            for (pR <- allFailedPricingRequests.distinct.toList) {
              strPriceBuilder.append("\t" + pR.toString)
            }
          }
          strPriceBuilder.append("****************** Pricing Requests Statistics ****************** \n")
          strPriceBuilder.append("// End of Test ********************************************** End of Test ************* // \n")
        }
        if (priceRequestWorkerCounter == totalPricingRequestWorkers && marketDataRequestWorkerCounter == totalMarketDataRequestWorkers) {
          determineShutdown()
        }
      }

      if (x._1 == MARKET_DATA_WORKER_SHUTDOWN) {
        marketDataRequestWorkerCounter = marketDataRequestWorkerCounter + 1
        if (marketDataRequestWorkerCounter == totalMarketDataRequestWorkers && loopCount == TestUtils.repeatTestCount) {
          strMDBuilder.append("Test Run Time: " + new SimpleDateFormat("dd-MM-yyyy:HH:mm:SS").format(new Date()))
          strMDBuilder.append("****************** MarketData Requests Statistics ******************")
          val totalMarketDataRequests = if (TestUtils.repeatTestCount == 0) marketDataRequestsSize else loopCount * marketDataRequestsSize
          strMDBuilder.append("Total MarketData Requests Processed = " + totalMarketDataRequests)
          strMDBuilder.append("Total MarketData Request WebSocket Connections = " + TestUtils.noOfMarketDataRequestWorkers)
          val totalTime = (System.currentTimeMillis - TestStarter.allMarketDataRequestsStartTime) / 1000
          strMDBuilder.append("Total time taken to process " + totalMarketDataRequests + " MarketData (in seconds) = " + totalTime)
          strMDBuilder.append("The pricing server did " + totalMarketDataRequests / totalTime + " MarketData requests in one second")
          strMDBuilder.append("Total Failed MarketData Requests " + allFailedMarketDataRequests.distinct.toList.size)
          strMDBuilder.append("****************** MarketData Requests Statistics ****************** \n")
        }
        if (priceRequestWorkerCounter == totalPricingRequestWorkers && marketDataRequestWorkerCounter == totalMarketDataRequestWorkers) {
          determineShutdown()
        }
      }
    }
  }

  def determineShutdown() = {
    masterActorLogger.info("End Stress Testing ************************************** \n")
    if (TestUtils.repeatTestCount == 0 || loopCount == TestUtils.repeatTestCount) {
      strMDBuilder.foreach((s: String) => masterActorLogger.info(s))
      strPriceBuilder.foreach((s: String) => masterActorLogger.info(s))
      context.system.shutdown()
      if (allFailedPricingRequests.isEmpty && allFailedMarketDataRequests.isEmpty) sys.exit(0) else sys.exit(-1)
    } else {
      resetTestVariables()
      TestStarter.startTesting()
    }
  }

  def resetTestVariables() = {
    loopCount = loopCount + 1
    priceRequestWorkerCounter = 0
    marketDataRequestWorkerCounter = 0
    masterActorLogger.info("Start Stress Testing ************* TestCount = " + loopCount)
  }
}