package com.egc.pricing.loadtest

import org.jfarcand.wcs.{MessageListener, TextListener, WebSocket}
import java.util.concurrent.{TimeUnit, Callable, Executors, ExecutorService}
import scala.collection.parallel.mutable
import scala.collection.JavaConversions
import com.google.common.base.Stopwatch
import scala.collection.JavaConverters._

object SimpleThreadingTest {
  def main(args: Array[String]): Unit = {

    val map: Map[String, String] = {
      args.map(arg => {
        val split: Array[String] = arg.split(':')
        if (split.length != 2) throw new IllegalStateException(arg + " is not valid")
        split.head -> split.tail.head
      }).toMap
    }

    val loops = map.getOrElse("loops", "10").toInt
    lazy val host = map.getOrElse("host", "somehost")
    lazy val port = map.getOrElse("port", "someport")
    lazy val threads = map.getOrElse("threads", "64").toInt
    lazy val method = map.getOrElse("method", "price")
    val pricingRequests = map.getOrElse("pricingRequests", "10000").toInt

    val mapOfSockets = new mutable.ParHashMap[Long, WebSocket]()

    var x = 0
    val startTime = System.currentTimeMillis()

    def getSocket(thread: Long): WebSocket = {
      val id: Long = Thread.currentThread().getId
      if (mapOfSockets.contains(id)) mapOfSockets.keys
      else {
        //println(s"Opening WebSocket Connection with ThreadId [$id]")
        val webSocket: WebSocket = WebSocket().open(s"ws://$host:$port/$method")
        webSocket.listener(
          new TextListener with MessageListener {
            override def onMessage(msg: String) {
              x = x + 1
              println("Message received " + x)
              if(x == pricingRequests || x == 9999) println("time taken to process " + pricingRequests  + " pricing requests is " + (System.currentTimeMillis - startTime))
              //println(s"ThreadID $id Price Response = $msg ")
            }

            override def onError(e: Throwable) {
              e.printStackTrace()
            }
          }
        )
        mapOfSockets.put(id, webSocket)
        webSocket
      }
    }

    val simpleJsonv5 = "" // Use a proper JSON here

    val executor: ExecutorService = Executors.newFixedThreadPool(threads)

    val collection = JavaConversions.asJavaCollection(
      (1 to pricingRequests) map { _ =>
        new Callable[Unit] {
          def call() = {
            val id: Long = Thread.currentThread().getId
            //println("ThreadId " + Thread.currentThread.getId + " Sending JSON message ")
            val socket = getSocket(id)

            socket.send(simpleJsonv5)
          }
        }
      })

    val sw = new Stopwatch()
    sw.start()

    executor.invokeAll(collection)

    println("Parent thread is " + Thread.currentThread().getId)
    //println("time taken to process " + sw.elapsed(TimeUnit.MILLISECONDS))

    //executor.shutdown()

  }
}