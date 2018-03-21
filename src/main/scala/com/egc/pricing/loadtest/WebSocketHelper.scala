package com.egc.pricing.loadtest

import org.jfarcand.wcs.{MessageListener, TextListener, WebSocket}
import akka.actor.ActorRef
import grizzled.slf4j.Logger


trait WebSocketHelper {

  // Send the message via the Socket
  def sendMessageToSocket(ws: WebSocket, json: String) = ws.send(json)

  // Create multiple WebSocket connections
  def createWebSocketConnections(host: String, port: Int, totalConnections: Int, connectionEndPoint: String): List[WebSocket] = (for (i <- 0 until totalConnections) yield WebSocket().open(s"ws://${host}:${port}/${connectionEndPoint}")).toList

  /**
   * Registers listeners for all the WebSockets
   * Note: Always the wsList.size == actorRef.size (No worries)
   * @param wsList
   * @param actorRef
   */
  def registerWebSocketListeners(wsList: List[WebSocket], actorRef: List[ActorRef], totalMessages: Int) = {
    val logger = Logger(classOf[WebSocketHelper])

    for(i <- wsList.indices) {
      val ws: WebSocket = wsList(i)
      ws.listener(new TextListener with MessageListener {

        // Register onMessage
        override def onMessage(msg: String) {
          actorRef(i) ! (msg, totalMessages)
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
          println("Closing the PricingRequest socket! " + i)
        }
      })
    }
  }

  def registerWebSocketListener(ws: WebSocket, actorRef: ActorRef, totalMessages: Int) = {
    val logger = Logger(classOf[WebSocketHelper])

    //for(i <- 0 until webSocket.size) {
      //val ws: WebSocket = wsList(i)
      ws.listener(new TextListener with MessageListener {

        // Register onMessage
        override def onMessage(msg: String) {
          actorRef ! (msg, totalMessages)
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
          println("Closing the PricingRequest socket! ")
        }
      })
    //}
  }
}
