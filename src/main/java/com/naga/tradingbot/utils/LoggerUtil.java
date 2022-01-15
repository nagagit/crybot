package com.naga.tradingbot.utils;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.SendResponse;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Service
public class LoggerUtil {

    private static final Logger logger = Logger.getLogger(LoggerUtil.class);

    @Autowired
    private Environment env;

    public void notify(String ticker, String text) {

        String apiToken = getApiToken(ticker);
        String chatId = getChatId(ticker);

        String urlString = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";
        //Add Telegram token (given Token is fake)
        //Add chatId (given chatId is fake)

        urlString = String.format(urlString, apiToken, chatId, text);

        try {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            InputStream is = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getApiToken(String ticker) {
        if(env.containsProperty("telegram.bot.binance." + ticker + ".api.token")) {
            return env.getProperty("telegram.bot.binance." + ticker + ".api.token");
        } else {
            return env.getProperty("telegram.bot.binance." + "ALTCOIN" + ".api.token");
        }
    }

    private String getChatId(String ticker) {
        if(env.containsProperty("telegram.bot.binance." + ticker + ".chat.id")) {
            return env.getProperty("telegram.bot.binance." + ticker + ".chat.id");
        } else {
            return env.getProperty("telegram.bot.binance." + "ALTCOIN" + ".chat.id");
        }
    }

    public boolean sendPhoto(String ticker, File file) {

        String apiToken = getApiToken(ticker);
        String chatId = getChatId(ticker);

        // Create your bot passing the token received from @BotFather
        TelegramBot bot = new TelegramBot(apiToken);
        // Register for updates
        bot.setUpdatesListener(updates -> {
            // ... process updates
            // return id of last processed update or confirm them all
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        SendResponse response = bot.execute(new SendPhoto(chatId, file));
        return response.isOk();
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void error(String message, Exception e) {
        logger.error(message, e);
    }

    public void error(String message) {
        logger.error(message);
    }
}