package com.egc.pricing.loadtest.util

import com.typesafe.config.ConfigFactory
import com.egc.ost.pricing.Logging.LogFileReader
import org.jfarcand.wcs.WebSocket
import grizzled.slf4j.Logger
import java.util.zip.{Deflater, Inflater}
import java.io.{PrintWriter, File, IOException, ByteArrayOutputStream}
import scala.io.Source
import com.egc.ost.pricing.contracts.enums.CalculationSpec._
import java.util.UUID
import com.egc.ost.pricing.contracts.request.{PricingRequest, MarketDataRequest}


case class TestParameters(logFilePath: String, confFilePath: String)
object TestUtils {

  val logger = Logger("TestUtils")

  val adapter = ConfigFactory.load("test.conf")

  // The Host and the Port for the pricing server
  val host: String = adapter.getString("host")
  val port: String = adapter.getString("port")

  // The location of the Log file reader
  lazy val testDataFileName: String = adapter.getString("testDataFileName")

  // Should the response Json be checked for validity?
  val isCheck: Boolean = adapter.getBoolean("isSanityCheck")

  // How many Workers should send request data to the server?
  val noOfMarketDataRequestWorkers = adapter.getInt("marketDataWorkerCount")
  val noOfPricingRequestWorkers = adapter.getInt("pricingWorkerCount")

  val testDataFilePath = getClass.getResource(s"/$testDataFileName").getPath
  val testData = Source.fromFile(testDataFilePath).getLines().toList
  lazy val reader: LogFileReader = new LogFileReader(testData)

  // The name of the WebSocket methods on the server
  val PRICE_WEB_SOCKET_METHOD = adapter.getString("wsPriceRequestMethod")
  val MARKET_DATA_WEB_SOCKET_METHOD = adapter.getString("wsMarketDataRequestMethod")
  val PING_ME_WEB_SOCKET_METHOD = "pingMeWs"

  // The actual number of pricing requests and market data requests to send
  val noOfPricingRequests = adapter.getInt("totalPricingRequests")
  val noOfMarketDataRequests = adapter.getInt("totalMarketDataRequests")

  val repeatTestCount = adapter.getInt("repeatTestCount")

  val testResultsFilePath = adapter.getString("testResultsFilePath")

  val npvTolerance = 1e-13
  val firstOrderDerivativeTolerance = 1e-10
  val genericToleranceLevel = 1e-8

  val firstOrderGreeks = Seq(STICKY_DELTA_DELTA, STICKY_STRIKE_DELTA, RHO)

  val marketDataRequestsJSON = TestUtils.reader.getMarketDataRequests map {
    case (id: UUID, (mdr: MarketDataRequest, json: String)) => json
  }
  val priceRequestsJSON = TestUtils.reader.getPricingRequests map {
    case (id: UUID, (pr: PricingRequest, json: String)) => json
  }

/*  def writeTestStatisticsToFile(testStatistics: List[String]) {
    val file = new File(filePath)

    def writeToFile(f: File)(printWriter: PrintWriter => Unit) = {
      val writer = new PrintWriter(f)
      try {
        printWriter(writer)
      } finally {
        writer.close()
      }
    }

    if (!file.exists) {
      writeToFile(file)(p => testStatistics.foreach(p.println))
    } else {
      val fileContent = Source.fromFile(filePath).getLines().toList
      val totalContent = fileContent ++ testStatistics
      writeToFile(file)(p => totalContent.foreach(p.println))
    }
  }*/

  def zipJson(jsonStr: String): Array[Byte] = {
    val byteArr = jsonStr.getBytes("UTF-8")

    println("The size of the un-compressed bytes are " + byteArr.size)

    val compressor = new Deflater()
    compressor.setLevel(Deflater.BEST_COMPRESSION)
    compressor.setInput(byteArr)

    val bos: ByteArrayOutputStream = new ByteArrayOutputStream(byteArr.length)

    compressor.finish()

    val buffer = new Array[Byte](1024)

    while(!compressor.finished()) {
      val bytesCompressed = compressor.deflate(buffer)
      bos.write(buffer, 0, bytesCompressed)
    }
    try {
      compressor.end()
      bos.close()
    } catch {
      case e : IOException => "What to do in case of an exception??"
    }

    println("The size of the compressed bytes are " + bos.toByteArray.size)
    bos.toByteArray
  }

  def unzipJson(byteArr: Array[Byte]): String = {

    println("The size of the ucompressed bytes are " + byteArr.size)
   // println("The ucompressed bytes are " + byteArr)
    val inflater = new Inflater()
    inflater.setInput(byteArr)

    val bos: ByteArrayOutputStream = new ByteArrayOutputStream(byteArr.length)

    val buffer = new Array[Byte](1024)

    while(!inflater.finished()) {
      val bytesUncompressed = inflater.inflate(buffer)
      bos.write(buffer, 0, bytesUncompressed)
    }

    try {
      bos.close
    } catch {
      case e : IOException => "What to do in case of an exception??"
    }

    println("The size of the un-compressed bytes are " + bos.toByteArray.size)
    bos.toString("UTF-8")
  }

  def createWebSocketConnections(count: Int, connType: String): List[WebSocket] = (for (i <- 0 until count) yield WebSocket().open(s"ws://${host}:${port}/${connType}")).toList
}