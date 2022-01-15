package com.naga.tradingbot.service;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.naga.tradingbot.model.data.PredictionEngine;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BotExecuteJob implements Job {

    private static final Logger logger = Logger.getLogger(BotExecuteJob.class);

    @Autowired
    private BotEngine botEngine;

    @Autowired
    private Environment env;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        List<String> tickers = botEngine.getClient().getExchangeInfo()
                .getSymbols().stream().filter(i -> i.getSymbol().endsWith("USDT"))
                .map(SymbolInfo::getSymbol)
                .filter(i -> !i.equals("BNBUSDT"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        if (env.getProperty("spring.profiles.active", String.class).equals("pi1")) {
            tickers = tickers.stream().limit(tickers.size() / 2).collect(Collectors.toList());
        } else if (env.getProperty("spring.profiles.active", String.class).equals("pi2")) {
            tickers = tickers.stream().skip(tickers.size() / 2).collect(Collectors.toList());
        }
        for (String ticker : tickers) {
            runBot(ticker);
        }
    }

    public void runBot(String ticker) {
        logger.info("Ticker : " + ticker);
        try {
            //get data from binance
            List<Candlestick> candlesticks = botEngine.gatherMAData(ticker);
            if (candlesticks.isEmpty())
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
