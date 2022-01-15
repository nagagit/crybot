package com.naga.tradingbot.service;

import com.binance.api.client.domain.market.Candlestick;
import com.naga.tradingbot.model.data.PredictionEngine;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BotExecuteEachCoinJob implements Job {

    private static final Logger logger = Logger.getLogger(BotExecuteJob.class);

    @Autowired
    private BotEngine botEngine;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        runBot(jobExecutionContext.getMergedJobDataMap().getString("ticker"));
    }

    public void runBot(String ticker) {
        try {
            //get data from binance
            List<Candlestick> candlesticks = botEngine.gatherMAData(ticker);
            if(candlesticks.isEmpty())
                return;
            //create chart and predict and output the BUY/SELL signal
            PredictionEngine predictionEngine = botEngine.predictTrendAndDecide(ticker, candlesticks);
            //BUY or SELL execution
            botEngine.tradeBasedOnSignal(ticker, predictionEngine);
        } catch (Exception e) {
            logger.error("There was an error during the main trading loop! {}", e);
        }
    }
}
