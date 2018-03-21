package com.egc.pricing.loadtest

import grizzled.slf4j.Logger
import com.typesafe.config.ConfigFactory
import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import org.jfarcand.wcs.{MessageListener, TextListener, WebSocket}
import scala.collection.mutable.ArrayBuffer

trait AkkaStressTestWithSimpleJsonTrait {

  val logger = Logger(classOf[AkkaStressTestWithSimpleJsonTrait])

  // Load the config entries
  val adapter = ConfigFactory.load("test.conf")

  // The Host and the Port for the pricing server
  val host: String = adapter.getString("host")
  val port: String = adapter.getString("port")

  // Should the response Json be checked for validity?
  val isCheck: Boolean = adapter.getBoolean("isSanityCheck")

  // How many Workers should send request data to the server?
  val noOfPricingRequestWorkers = adapter.getInt("pricingWorkerCount")

  // The name of the WebSocket methods on the server
  val PRICE_WEB_SOCKET_METHOD = adapter.getString("wsPriceRequestMethod")

  // The actual number of pricing requests and market data requests to send
  val noOfPricingRequests = adapter.getInt("totalPricingRequests")

  val PRICE_WORKER_SHUTDOWN = "priceWorkerShutdown"

  val complexJson: String = ""
  //val x = 300
  //val y = 500

  val simpleJSONv4European = ""
  val simpleJsonv4American = ""

  val simpleJSONv5Europen = ""
  val simpleJsonv5American = ""

  val lst: ArrayBuffer[String] = new ArrayBuffer[String]()
  val jsonList: List[String] = (for (i <- 0 until noOfPricingRequests) yield lst :+ simpleJSONv4European).flatten.toList

  //logger.info("Total size of Input data ************************************** " + jsonList.size)
  logger.info(s"Running against host $host and port $port")

  // Create the Actor system
  val system = ActorSystem.create("StressTest3")
  val masterActor = system.actorOf(Props(new MasterActor), name = "masterActor")

  // Create the ActorRef instances based on the number of (oOfPricingRequestWorkers)
  val priceActorRef: List[ActorRef] = (for (i <- 0 until noOfPricingRequestWorkers) yield system.actorOf(Props(new PriceRequestWorker), name = "priceRequest" + i)).toList

  val priceStartTime = System.currentTimeMillis
  //logger.info("Price StartTime: "    + priceStartTime)

  // Create multiple WebSocket connections for getMarketDataRequest based on the number of (noOfMarketDataRequestWorkers & noOfPricingRequestWorkers)
  val priceWebSocketList: List[WebSocket] = createWebSocketConnections(noOfPricingRequestWorkers, "price")
  //val priceWebSocketList: List[WebSocket] = createWebSocketConnections(noOfPricingRequestWorkers, "analyticHestonNpv")

  logger.info(s"Successfully created $noOfPricingRequestWorkers WebSocket connections")

  // Register the WebSocket listeners for price requests
  registerWebSocketListeners(priceWebSocketList, priceActorRef)

  // The actual Actor that receives and responds to the messages for all Pricing requests
  class PriceRequestWorker extends Actor {
    val priceLogger = Logger(classOf[PriceRequestWorker])

    var totalMessages = 0
    var priceResponseCounter = 0
    var priceStartTime = System.currentTimeMillis
    var msgCount = 0

    def receive = {
      case lstChunkStr: (List[String], WebSocket) =>
        totalMessages = lstChunkStr._1.size

        lstChunkStr._1.foreach((json: (String)) => {
          msgCount = msgCount + 1
          sendMessageToSocket(lstChunkStr._2, json)
        })
      case jsonResponse: String =>
        priceResponseCounter = priceResponseCounter + 1
        //priceLogger.info("Price Response is " + jsonResponse)
        if(priceResponseCounter == totalMessages) {
          context.stop(self)
          // Send message to the Master Actor to shutdown!
          masterActor ! PRICE_WORKER_SHUTDOWN
        }
    }
  }

  //val priceStartTime = System.currentTimeMillis

  class MasterActor extends Actor {

    val masterActorLogger = Logger(classOf[MasterActor])

    var priceRequestWorkerCounter = 0
    var marketDataRequestWorkerCounter = 0

    def receive = {
      case x: String =>
        if (x == PRICE_WORKER_SHUTDOWN) {
          priceRequestWorkerCounter = priceRequestWorkerCounter + 1
          if (priceRequestWorkerCounter == noOfPricingRequestWorkers) {
            masterActorLogger.info("************** Time taken to process " + jsonList.size + " PricingRequests (in Milli Seconds): " + (System.currentTimeMillis - priceStartTime))
            //masterActorLogger.info("************** Roughly it is: " + jsonList.size / ((System.currentTimeMillis - priceStartTime).toDouble / 1000.0) + " pricing requests per second")
            masterActorLogger.info("End Stress Testing ************************************** ")
            context.system.shutdown()
            sys.exit(0)
          }
        }
    }
  }

  /************************ Utility methods ****************************/

  // Send the message via the Socket
  def sendMessageToSocket(ws: WebSocket, json: String) = ws.send(json)

  def sendMessageToSocketArr(ws: WebSocket, json: Array[Byte]) = ws.send(json)

  // Create multiple WebSocket connections
  def createWebSocketConnections(count: Int, connType: String): List[WebSocket] = (for (i <- 0 until count) yield WebSocket().open(s"ws://${host}:${port}/${connType}")).toList

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
  def splitList[A](xs: List[A], n: Int): List[List[A]] = {
    val (quot, rem) = (xs.size / n, xs.size % n)
    val (smaller, bigger) = xs.splitAt(xs.size - rem * (quot + 1))
    (smaller.grouped(quot) ++ bigger.grouped(quot + 1)).toList
  }

  /**
   * Registers listeners for all the WebSockets
   * Note: Always the wsList.size == actorRef.size (No worries)
   * @param wsList
   * @param actorRef
   */
  def registerWebSocketListeners(wsList: List[WebSocket], actorRef: List[ActorRef]) = {
    for(i <- wsList.indices) {
      val ws: WebSocket = wsList(i)
      ws.listener(new TextListener with MessageListener {

        // Register onMessage
        override def onMessage(msg: String) {
          actorRef(i) ! msg
        }

        // Register onError
        override def onError(t : scala.Throwable) {
          logger.error("Error occurred when listening for messages from the Server. Please see the Stacktrace below!")
          t.printStackTrace()
          logger.error("Exiting!!! *** ")
          sys.exit(0)
        }

        // Register onClose
        override def onClose {
          logger.info("Closing the PricingRequest socket! " + i)
        }
      })
    }
  }
}