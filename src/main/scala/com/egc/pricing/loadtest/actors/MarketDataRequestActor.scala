package com.egc.pricing.loadtest.actors

import akka.actor.{ActorRef, Actor}
import grizzled.slf4j.Logger
import org.jfarcand.wcs.WebSocket
import com.egc.ost.pricing.contracts.response.MarketDataResponse
import com.egc.ost.pricing.contracts.json.support.JsonMarshall
import java.util.UUID
import com.egc.pricing.loadtest.util.TestUtils
import com.egc.pricing.loadtest.{WebSocketHelper, TestStarter}
import scala.collection.mutable.ListBuffer


class MarketDataRequestActor extends Actor with WebSocketHelper {

  val mdLogger = Logger(classOf[MarketDataRequestActor])

  var webSocketEndpoint: WebSocket = null
  var responseHandler: ActorRef = null
  var masterActorRef: ActorRef = TestStarter.masterActor
  var isWebSocketsNotRegistered = true

  var allPricingRequestsStartTime = 0L

  def receive = {
    case requestMessage: (List[String], WebSocket, ActorRef) => {
      if (isWebSocketsNotRegistered) {
        webSocketEndpoint = requestMessage._2
        responseHandler = requestMessage._3
        registerWebSocketListener(webSocketEndpoint, responseHandler, requestMessage._1.size)
        isWebSocketsNotRegistered = false
      }
      // Iterate and send the messages
      requestMessage._1.foreach((json: (String)) => {
        webSocketEndpoint.send(json)
      })
    }
  }
}

class MarketDataResponseActor extends Actor {

  var totalMessages = 0
  var mdResponseCounter = 0
  val MARKET_DATA_WORKER_SHUTDOWN = "marketDataWorkerShutdown"

  var masterActorRef: ActorRef = TestStarter.masterActor
  var failedMarketDataResponses = ListBuffer.empty[String]

  def receive = {
    case jsonResponse: (String, Int) =>
      if(totalMessages == 0) totalMessages = jsonResponse._2
      mdResponseCounter = mdResponseCounter + 1
      val response: MarketDataResponse = JsonMarshall.deserialize[MarketDataResponse](jsonResponse._1)
      val id: UUID = response.id
      if (TestUtils.isCheck) checkMarketDataResults(TestUtils.reader.getMarketDataResponses(id)._1, response)

      if (mdResponseCounter == totalMessages) {
        mdResponseCounter = 0
        totalMessages = 0
        masterActorRef ! (MARKET_DATA_WORKER_SHUTDOWN, failedMarketDataResponses)
      }
  }

  def checkMarketDataResults(logFileResponse: MarketDataResponse, actualResponse: MarketDataResponse) = {
    val logFileItems = logFileResponse.items
    actualResponse.items.foreach((f: (String, Double)) => {
      val value = logFileItems.getOrElse(f._1, 0.0)
      if (value != f._2) failedMarketDataResponses.+(f._1)
    })
  }
}
