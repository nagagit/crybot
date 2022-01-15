package com.naga.tradingbot;

import com.naga.tradingbot.service.BotEngine;
import com.naga.tradingbot.service.BotExecuteEachCoinJob;
import com.naga.tradingbot.service.BotExecuteJob;
import com.naga.tradingbot.utils.LoggerUtil;
import org.apache.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Date;

@SpringBootApplication
public class TradingBotApplication {
    private static final Logger logger = Logger.getLogger(TradingBotApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(TradingBotApplication.class, args);
        BotEngine dolores = context.getBean(BotEngine.class);
        Environment env = context.getBean(Environment.class);
        BotExecuteJob botExecuteJob = context.getBean(BotExecuteJob.class);
        BotExecuteEachCoinJob botExecuteEachCoinJob = context.getBean(BotExecuteEachCoinJob.class);
        LoggerUtil loggerUtil = context.getBean(LoggerUtil.class);

        String binanceApiKey = env.getProperty("trading.bot.binance.api.key");
        String binanceSecretKey = env.getProperty("trading.bot.binance.secret.key");

        if (StringUtils.isEmpty(binanceApiKey) || StringUtils.isEmpty(binanceSecretKey)) {
            logger.error("Binance Credentials not set in application.properties!");
            System.exit(-1);
        } else {
            dolores.setBinanceCreds(binanceApiKey, binanceSecretKey);
            logger.info("Bot started");
        }
        try {
            for (; ; ) {
                loggerUtil.notify("BTCUSDT", "Run started @ " + new Date() + " on "
                        + env.getProperty("spring.profiles.active", String.class));
                botExecuteJob.execute(null);
                System.gc();
                Thread.sleep(1 * 30 * 60 * 1000l);

            }
        } catch (Exception e) {
            loggerUtil.notify("BTCUSDT", "FATAL.... Main Thread stopped. Bot not running.");
        }
        //botExecuteEachCoinJob.runBot("ONEUSDT");
    }
}
