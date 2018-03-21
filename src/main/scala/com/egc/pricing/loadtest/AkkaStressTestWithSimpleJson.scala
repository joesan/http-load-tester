package com.egc.pricing.loadtest


object AkkaStressTestWithSimpleJson extends AkkaStressTestWithSimpleJsonTrait {

  def main(args: Array[String]) = {
    try {
      logger.info("Start Stress Testing ************************************** ")
      startTesting()
    } catch {
      case t: Throwable => {
        logger.error("Error occurred when Stress testing the pricing engine. See the Stacktrace below!")
        t.printStackTrace()
        logger.error("Exiting!!! *** ")
        sys.exit(0)
      }
    }
  }

  def startTesting() = {
    val splittedPriceInputList = splitList[String](jsonList, noOfPricingRequestWorkers)
    //logger.info("Total MarketRequestData chunks (must be equal to marketDataWorkerCount from the controllers.test.conf file) " + splittedMarketDataInputList.size)
    logger.info("Total PriceRequestData chunks  (must be equal to pricingWorkerCount from the controllers.test.conf file) " + splittedPriceInputList.size)
    for(x <- splittedPriceInputList.indices) {
      priceActorRef(x) ! (splittedPriceInputList(x), priceWebSocketList(x))
    }
    logger.info("*** Successfully sent messages to all PriceRequest Actor")
  }
}