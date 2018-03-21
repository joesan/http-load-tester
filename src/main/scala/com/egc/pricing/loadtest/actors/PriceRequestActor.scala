package com.egc.pricing.loadtest.actors

import akka.actor.{ActorRef, Actor}
import org.jfarcand.wcs.WebSocket
import com.egc.pricing.loadtest.util.TestUtils
import grizzled.slf4j.Logger
import com.egc.ost.pricing.contracts.response.{PricingResult, ResultSet}
import com.egc.ost.pricing.contracts.json.support.JsonMarshall
import java.util.UUID
import com.egc.ost.pricing.contracts.enums.CalculationSpec
import com.egc.ost.pricing.contracts.enums.CalculationSpec._
import scala.collection.mutable.ListBuffer
import com.egc.pricing.loadtest.{TestStarter, WebSocketHelper}

class PriceRequestActor extends Actor with WebSocketHelper {

  val logger = Logger("PriceRequestActor")

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

class PriceResponseActor extends Actor {

  var totalMessages = 0
  var priceResponseCounter = 0

  var masterActorRef: ActorRef = TestStarter.masterActor

  val npvTolerance = 1e-13
  val firstOrderDerivativeTolerance = 1e-10
  val genericToleranceLevel = 1e-8

  var failedPriceResponses = ListBuffer.empty[String]

  val firstOrderGreeks = Seq(STICKY_DELTA_DELTA, STICKY_STRIKE_DELTA, RHO)

  val PRICE_WORKER_SHUTDOWN = "priceWorkerShutdown"

  def receive = {
    case jsonResponse: (String, Int) =>
      if(totalMessages == 0) totalMessages = jsonResponse._2
      priceResponseCounter = priceResponseCounter + 1
      val response: ResultSet = JsonMarshall.deserialize[ResultSet](jsonResponse._1)
      val id: UUID = response.id
      if (TestUtils.isCheck) checkPriceResponse(TestUtils.reader.getResultSets(id)._1, response)

      if (priceResponseCounter == totalMessages) {
        priceResponseCounter = 0
        totalMessages = 0
        masterActorRef ! (PRICE_WORKER_SHUTDOWN, failedPriceResponses)
      }
  }

  def checkPriceResponse(expectedResponse: ResultSet, actualResponse: ResultSet): Unit = {

    def getPricingDifference(cs: CalculationSpec, expected: Seq[PricingResult], actual: Seq[PricingResult]) = {
      val uuid = expectedResponse.id.toString
      val expectedGreeks = ResultSet.getGreeks(cs, expected).sortBy(_._1)
      val actualGreeks = ResultSet.getGreeks(cs, actual).sortBy(_._1)

      def relDiff(expectedValue: Double, actualValue: Double): Double = math.abs(expectedValue - actualValue) / expectedValue

      def isRelativeDiffExceeded(expectedValue: Double, actualValue: Double, toleranceLevel: Double) = relDiff(expectedValue, actualValue) > toleranceLevel

      def log(actualName: String, expectedValue: Double, actualValue: Double): String = s"UUID -> $uuid, CalculationSpec -> $cs, Greek -> $actualName -> $expectedValue was not $actualValue [Difference = ${relDiff(expectedValue, actualValue)}]"

      // Filter the greeks that are not equal
      val filteredGreeks = expectedGreeks.zip(actualGreeks).filter {
        case ((expectedName, expectedValue), (actualName, actualValue)) => !expectedValue.equals(actualValue)
      }

      def isGreekNotWithinTolerance(expectedValue: Double, actualValue: Double, f: (Double, Double, Double) => Boolean): Boolean = {
        val isNPVTolerant = cs == NPV && f(expectedValue, actualValue, npvTolerance)
        val isFirstOrderGreekTolerant = firstOrderGreeks.contains(cs) && f(expectedValue, actualValue, firstOrderDerivativeTolerance)
        val isOtherGreekTolerant = f(expectedValue, actualValue, genericToleranceLevel)
        isNPVTolerant || isFirstOrderGreekTolerant || isOtherGreekTolerant
      }

      filteredGreeks.collect {
        case ((_, expectedValue), (actualName, actualValue)) if isGreekNotWithinTolerance(expectedValue, actualValue, isRelativeDiffExceeded) => log(actualName, expectedValue, actualValue)
      }
    }

    val dealLetterGreeks: Seq[String] =
      CalculationSpec.values().map {
        case cs =>
          val expected: Seq[PricingResult] = expectedResponse.dealLetterGreeks
          val actual: Seq[PricingResult] = actualResponse.dealLetterGreeks
          getPricingDifference(cs, expected, actual)
      }.flatten.toSeq

    val riskFactorGreeks: Seq[String] =
      CalculationSpec.values().map {
        case cs => {
          val expected: Seq[PricingResult] = expectedResponse.riskFactorGreeks
          val actual: Seq[PricingResult] = actualResponse.riskFactorGreeks
          getPricingDifference(cs, expected, actual)
        }
      }.flatten.toSeq

    if (dealLetterGreeks.nonEmpty) failedPriceResponses.appendAll(dealLetterGreeks)
    if (riskFactorGreeks.nonEmpty) failedPriceResponses.appendAll(riskFactorGreeks)
  }
}