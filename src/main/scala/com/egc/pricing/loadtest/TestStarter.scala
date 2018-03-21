package com.egc.pricing.loadtest

import com.egc.pricing.loadtest.util.TestUtils
import akka.actor.{ActorRef, Props, ActorSystem}
import com.egc.pricing.loadtest.actors._
import org.jfarcand.wcs.WebSocket
import grizzled.slf4j.Logger


object TestStarter extends WebSocketHelper {

  val testStarterLogger = Logger("TestStarter")

  val marketDataRequestsJSON = TestUtils.marketDataRequestsJSON
  val priceRequestsJSON = TestUtils.priceRequestsJSON

  val splittedMarketDataInputList = splitList[String](marketDataRequestsJSON.toList, TestUtils.noOfMarketDataRequestWorkers)
  val splittedPriceInputList = splitList[String](priceRequestsJSON.toList, TestUtils.noOfPricingRequestWorkers)

  // Create the Actor system
  val system = ActorSystem.create("StressTest")
  val masterActor = system.actorOf(Props(new MasterActor), name = "masterActor")

  def createActorReferences(props: Props, noOfWorkers: Int, name: String) = (for (i <- 0 until noOfWorkers) yield system.actorOf(props, name + i)).toList

  // Create the ActorRef instances based on the number of (noOfMarketDataRequestWorkers & noOfPricingRequestWorkers)
  val marketDataActorRef: List[ActorRef] = createActorReferences(Props(new MarketDataRequestActor), TestUtils.noOfMarketDataRequestWorkers, "marketDataRequest")
  val priceActorRef: List[ActorRef] = createActorReferences(Props(new PriceRequestActor), TestUtils.noOfPricingRequestWorkers, "priceRequest")
  // Actors for handling the responses
  val marketDataResponseActorRef: List[ActorRef] = createActorReferences(Props(new MarketDataResponseActor), TestUtils.noOfMarketDataRequestWorkers, "marketDataResponse")
  val priceResponseActorRef: List[ActorRef] = createActorReferences(Props(new PriceResponseActor), TestUtils.noOfPricingRequestWorkers, "priceResponse")

  // Create multiple WebSocket connections for getMarketDataRequest based on the number of (noOfMarketDataRequestWorkers & noOfPricingRequestWorkers)
  val allMarketDataRequestsStartTime = System.currentTimeMillis()
  val marketDataWebSocketList: List[WebSocket] = createWebSocketConnections(TestUtils.host, TestUtils.port.toInt, TestUtils.noOfMarketDataRequestWorkers, "getMarketData")

  val allPricingRequestsStartTime = System.currentTimeMillis()
  val priceWebSocketList: List[WebSocket] = createWebSocketConnections(TestUtils.host, TestUtils.port.toInt, TestUtils.noOfPricingRequestWorkers, "price")

  testStarterLogger.info("Total MarketRequestData chunks (must be equal to marketDataWorkerCount from the test.conf file) " + splittedMarketDataInputList.size)
  testStarterLogger.info("Total PriceRequestData chunks  (must be equal to pricingWorkerCount from the test.conf file) " + splittedPriceInputList.size)

  def startTesting() = {
    if (TestUtils.repeatTestCount == 0) {
      testStarterLogger.info("The repeatTestCount in the test.conf file is set to 0, so the tests was not run. Please set it to a value > 0 and run the tests again!")
      sys.exit(-1)
    }

    // Iterate through the ActorRef instances and send messages
    for (x <- splittedMarketDataInputList.indices) {
      marketDataActorRef(x) ! (splittedMarketDataInputList(x), marketDataWebSocketList(x), marketDataResponseActorRef(x))
    }
    testStarterLogger.info("*** Successfully sent messages to all MarketDataRequest Actor")
    for (x <- splittedPriceInputList.indices) {
      priceActorRef(x) ! (splittedPriceInputList(x), priceWebSocketList(x), priceResponseActorRef(x))
    }
    testStarterLogger.info("*** Successfully sent messages to all PriceRequest Actor")
  }

  /**
   * Split the List into chunks of List as specified by n
   * For example., if the input is List(1,2,3,4,5,6), and n
   * is 2, then the result is:
   * List(List(1,2,3), List(4,5,6))
   * @param xs
   * @param n
   * @tparam A
   * @return
   */
  private def splitList[A](xs: List[A], n: Int): List[List[A]] = {
    val (quot, rem) = (xs.size / n, xs.size % n)
    val (smaller, bigger) = xs.splitAt(xs.size - rem * (quot + 1))
    (smaller.grouped(quot) ++ bigger.grouped(quot + 1)).toList
  }
}