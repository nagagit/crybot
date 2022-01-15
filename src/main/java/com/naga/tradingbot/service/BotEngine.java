package com.naga.tradingbot.service;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerStatistics;
import com.naga.tradingbot.model.data.PredictionEngine;
import com.naga.tradingbot.utils.CalcUtils;
import com.naga.tradingbot.utils.LoggerUtil;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static com.binance.api.client.domain.account.NewOrder.*;

@Service
public class BotEngine {

    @Autowired
    private LoggerUtil loggerUtil;

    @Autowired
    private MovingAverageChartHelper movingAverageChartHelper;

    @Value("${developmentMode}")
    public boolean DEVELOPMENT_MODE;

    private BinanceApiRestClient client;

    public BinanceApiRestClient getClient() {
        return client;
    }

    public void setClient(BinanceApiRestClient client) {
        this.client = client;
    }

    @Autowired
    private Environment env;

    public Double getCurrentPrice(String ticker) {
        TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker);
        return Double.valueOf(tickerStatistics.getLastPrice());
    }

    /**
     * Returns the order history to the UI for displaying in the /orders endpoint
     *
     * @return String (HTML) of the order history
     */
    public String getOrderHistory(String ticker) {
        String response = "";
        List<Trade> trades = client.getMyTrades(ticker);
        String cryptoCoin = ticker.replace("USDT", "");
        for (Trade trade : trades) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy' 'HH:mm:ss:S");
            response =
                    new StringBuilder()
                            .append("<br><font color=\"")
                            .append(trade.isBuyer() ? "green" : "red")
                            .append("\">")
                            .append(trade.getOrderId())
                            .append(": Date/Time: ")
                            .append(simpleDateFormat.format(trade.getTime()))
                            .append(": ")
                            .append(trade.getQty())
                            .append(" ")
                            .append(cryptoCoin)
                            .append(" @ $")
                            .append(String.format("%.2f", Double.valueOf(trade.getPrice())))
                            .append("</font>")
                            .append(response)
                            .toString();
        }
        return response;
    }

    /**
     * Returns the current balances to the UI for displaying in the /status endpoint
     *
     * @return String (HTML) of the current balance
     */
    public String getBalances() {
        StringBuilder response = new StringBuilder();
        Account account = client.getAccount();
        List<AssetBalance> balances = account.getBalances();
        for (AssetBalance balance : balances) {
            Double amount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
            if (amount > 0.0) {
                response
                        .append("<br>&nbsp;&nbsp;-&nbsp;")
                        .append(amount)
                        .append(" ")
                        .append(balance.getAsset());
            }
        }
        return response.toString();
    }

    /**
     * Sets the credentials that are needed for interacting with Binance
     *
     * @param binanceAPIKey    Binance API Key
     * @param binanceAPISecret Binance API Secret
     */
    public void setBinanceCreds(String binanceAPIKey, String binanceAPISecret) {
        loggerUtil.debug("Setting Binance credentials");
        BinanceApiClientFactory factory =
                BinanceApiClientFactory.newInstance(binanceAPIKey, binanceAPISecret);
        client = factory.newRestClient();
    }

    /**
     * Returns the total balance of the account in current estimated BTC
     *
     * @return Balance in BTC
     */
    public String getCurrentBalanceInBTC() {
        Account account = client.getAccount();
        // Pull the latest account balance info from Binance
        List<AssetBalance> balances = account.getBalances();
        Double estimatedBalance = 0.0;
        for (AssetBalance balance : balances) {
            Double amount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
            if (amount > 0.0) {
                if (balance.getAsset().equals("BTC")) {
                    estimatedBalance += amount;
                } else {
                    estimatedBalance += valueInBTC(amount, balance.getAsset());
                }
            }
        }
        estimatedBalance = CalcUtils.roundTo(estimatedBalance, 8);
        return estimatedBalance.toString();
    }

    /**
     * Returns the total balance of the account in current estimated BTC
     *
     * @return Balance in BTC
     */
    public Double getTotalBalanceInUSDT() {
        Account account = client.getAccount();
        // Pull the latest account balance info from Binance
        List<AssetBalance> balances = account.getBalances();
        Double estimatedBalance = 0.0;
        for (AssetBalance balance : balances) {
            Double amount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
            if (amount > 0.0) {
                if (balance.getAsset().equals("USDT")) {
                    estimatedBalance += amount;
                } else {
                    estimatedBalance += valueInUSDT(amount, balance.getAsset());
                }
            }
        }
        estimatedBalance = CalcUtils.roundTo(estimatedBalance, 8);
        return estimatedBalance;
    }

    /**
     * Returns the total balance of the account in current estimated BTC
     *
     * @return Balance in BTC
     */
    public Double getCurrentUSDTBalanceForATicker(String ticker) {
        Account account = client.getAccount();
        AssetBalance balance = account.getAssetBalance(ticker);
        Double estimatedBalance = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
        TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker + "USDT");
        return estimatedBalance * Double.valueOf(tickerStatistics.getLastPrice());
    }

    /**
     * Estimate the value of a given amount/ticker in BTC
     *
     * @param amount The amount of an asset
     * @param ticker The ticker of the asset to estimate
     */
    private Double valueInBTC(Double amount, String ticker) {
        if (ticker.equals("USDT")) {
            TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker);
            return amount / Double.valueOf(tickerStatistics.getLastPrice());
        } else {
            return Double.valueOf(client.get24HrPriceStatistics(ticker + "BTC").getLastPrice()) * amount;
        }
    }

    /**
     * Estimate the value of a given amount/ticker in BTC
     *
     * @param amount The amount of an asset
     * @param ticker The ticker of the asset to estimate
     */
    private Double valueInUSDT(Double amount, String ticker) {
        TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker + "USDT");
        return amount * Double.valueOf(tickerStatistics.getLastPrice());
    }

    /**
     * Retrieves data from the ticker data pulled from Binance. This data is then used later for
     * predicting a selling price.
     */
    public List<Candlestick> gatherMAData(String ticker) {
        List<Candlestick> candlesticks = new LinkedList<>();
        Calendar calendar = null;
        try {
            // Make the GET call to Binance
            for (int i = 12; i > 0; i--) {
                calendar = Calendar.getInstance();
                calendar.add(Calendar.MONTH, -i);
                Long startTime = calendar.getTimeInMillis();
                calendar = Calendar.getInstance();
                if (i > 2)
                    calendar.add(Calendar.MONTH, -(i - 1));
                Long endTime = calendar.getTimeInMillis();
                candlesticks.addAll(client.getCandlestickBars(ticker, CandlestickInterval.HOURLY, 1000, startTime, endTime));
                new CalcUtils().sleeper(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
            new CalcUtils().sleeper(120000);
        }
        return candlesticks;
    }


    /**
     * Use the gathered data to attempt to predict a price to sell at, and then a price to buy back
     * at. When the price exceeds that target value, perform a sell and a buy back to make an
     * incremental amount of money.
     */
    public void tradeBasedOnSignal(String ticker, PredictionEngine predictionEngine) {
        Double lastTargetPrice = 1000000.0;
        Double buyBackPrice = 0.0;
        boolean marketBuy = false;
        String message = "";
        if (DEVELOPMENT_MODE) {
            reportDevMode();
        }
        if (predictionEngine.getTradeSignal().equals("DONOTHING")) {
            return;
        }
        // Gather data calculate, and update target price and buy back
        predictionEngine.executeThoughtProcess();
        lastTargetPrice = predictionEngine.getTargetPrice();
        // Find current price and decide to sell
        buyBackPrice = CalcUtils.roundTo(predictionEngine.getCurrentPrice() * PredictionEngine.buyBackAfterThisPercentage, 8);
        if (predictionEngine.getCurrentPrice() > lastTargetPrice) {
            lastTargetPrice = predictionEngine.getCurrentPrice();
            predictionEngine.setTargetPrice(predictionEngine.getCurrentPrice());
        }
        List<Order> openOrders = client.getOpenOrders(new OrderRequest(ticker));
        if (!openOrders.isEmpty()) {
            loggerUtil.debug("Number of open " + ticker + " orders: " + openOrders.size());
            Order openOrder = openOrders.get(0);
            if (openOrder != null) {
                Double currentMargin = predictionEngine.getCurrentPrice() / Double.valueOf(openOrder.getPrice());
                Double currentMarginPercent = CalcUtils.roundTo((currentMargin - 1) * 100, 2);
                Double buyBackDifference =
                        CalcUtils.roundTo((predictionEngine.getCurrentPrice() - Double.valueOf(openOrder.getPrice())), 2);
                loggerUtil.debug(
                        "Current buy back: " + currentMarginPercent + "% ($" + buyBackDifference + ")");
                if ((currentMarginPercent > 10
                        || (System.currentTimeMillis() - openOrder.getTime()) > 432000000 /*5days*/
                ) && predictionEngine.getTradeSignal().equalsIgnoreCase("GOOD BUY")) {
                    message = "Deciding to submit a market buy back at $" + predictionEngine.getCurrentPrice()
                            + " currentMarginPercent - " + currentMarginPercent + "%" + " OpenOrderAge - "
                            + (System.currentTimeMillis() - openOrder.getTime()) / (1000 * 60 * 60 * 24) + " days and tradeSignal - "
                            + predictionEngine.getTradeSignal();
                    loggerUtil.info(message);
                    loggerUtil.notify(ticker, message);
                    if (!DEVELOPMENT_MODE) {
                        marketBuy = executeMarketBuyBack(ticker);
                    } else {
                        reportDevMode();
                    }
                }
            }
        }
        message = "CurrentPrice at $" + predictionEngine.getCurrentPrice() + " and LastTargetPrice at $" + lastTargetPrice;
        loggerUtil.info(message);
        if (!marketBuy) {
            // Find out how much free asset there is to trade
            if (!DEVELOPMENT_MODE) {
                try {
                    performSellAndBuyBack(ticker, predictionEngine, buyBackPrice, lastTargetPrice);
                } catch (Exception e) {
                    loggerUtil.error("Exception occurred during Sell And Buy Back", e);
                    loggerUtil.notify(ticker, "Error at performSellAndBuyBack method. error message - " + e.getMessage());
                }
            } else {
                reportDevMode();
            }
        } else {
            message = "market bought. So skipping SellAndBuyBack action";
            loggerUtil.info(message);
            loggerUtil.notify(ticker, message);
        }
    }

    /**
     * Perform a sell and buy at the passed in values. Uses the Binance configuration to execute these
     * trades.
     *
     * @param predictionEngine PredictionEngine
     * @param buyPrice         Price to buy at
     * @param lastTargetPrice
     */
    private void performSellAndBuyBack(String ticker, PredictionEngine predictionEngine, Double buyPrice, Double lastTargetPrice) throws Exception {
        Double sellPrice = predictionEngine.getCurrentPrice();
        String message = "";
        String cryptoCoin = ticker.replace("USDT", "");
        boolean sellIt = Boolean.FALSE;
        Account account = client.getAccount();
        // Find out how much free asset there is to trade
        Double freeCoinFloored =
                CalcUtils.floorTo(Double.valueOf(account.getAssetBalance(cryptoCoin).getFree()), String.valueOf(sellPrice.intValue()).length() - 1);
        /**
         * Don't sell for loss condition starts
         */
        //TODO: refactor this as method
        AssetBalance balance = account.getAssetBalance(cryptoCoin);
        Double sellableAmount = Double.valueOf(balance.getFree());
        boolean stopLossSignal = false;
        boolean sellForProfitSignal = false;
        List<SymbolFilter> symbolFilters = client.getExchangeInfo().getSymbolInfo(ticker).getFilters();
        Double minPrice = Double.valueOf(symbolFilters.stream().filter(i -> i.getFilterType().name().equals(FilterType.PRICE_FILTER.name()))
                .findFirst().get().getMinPrice());
        int floorDecimal = 0;
        Double temp = minPrice;
        while (temp < 1) {
            temp *= 10;
            floorDecimal++;
        }
        DecimalFormat df = new DecimalFormat("#.#");
        df.setMaximumFractionDigits(floorDecimal);
        if (sellableAmount > 0.0) {
            List<Trade> trades = client.getMyTrades(ticker, 1);
            if (trades != null && !trades.isEmpty() && trades.get(0).isBuyer()) {
                Double lastBuyPrice = Double.valueOf(trades.get(0).getPrice());
                message = cryptoCoin + " Last Buy Price : " + lastBuyPrice;
                loggerUtil.info(message);
                //loggerUtil.notify(ticker, message);
                stopLossSignal = ((predictionEngine.getCurrentPrice() / lastTargetPrice) < 0.80) || predictionEngine.getTradeSignal().equalsIgnoreCase("SELL");
                sellForProfitSignal = sellPrice >= (lastBuyPrice * PredictionEngine.sellPriceMultiplier);
                sellIt = sellForProfitSignal || stopLossSignal;
                if(stopLossSignal)
                    loggerUtil.info("stopLossSignal is true");
            } else {
                //May be coins transferred to Binance from another exhcange. That's why no historical trades available. So good to sell it
                sellIt = Boolean.TRUE;
            }
            if (!sellIt) {
                if (!sellForProfitSignal) {
                    message = "sellPrice >= (lastBuyPrice * AvgPredictionEngine.sellPriceMultiplier) conditions not met";
                } else if (!stopLossSignal) {
                    message = "stopLossSignal is false";
                }
                loggerUtil.info(message);
                //loggerUtil.notify(ticker, message);
            }
        }
        /**
         * Avoid sell for loss condition ends
         */
        if (freeCoinFloored > 0.0001 && sellIt) {
            //TODO: Refactor to limitSell method
            loggerUtil.info("Amount of " + cryptoCoin + " to trade: " + freeCoinFloored);
            try {
                message = "Executing sell of: " + freeCoinFloored + " " + cryptoCoin + " @ $" + df.format(sellPrice);
                loggerUtil.info(message);
                loggerUtil.notify(ticker, message);
                // Submit the binance sell
                NewOrderResponse performSell =
                        client.newOrder(
                                limitSell(
                                        ticker,
                                        TimeInForce.GTC,
                                        freeCoinFloored.toString(),
                                        df.format(sellPrice)));
                loggerUtil.info("Limit Sell submitted: " + performSell.getTransactTime());
                loggerUtil.notify(ticker, "Limit Sell submitted");
                new CalcUtils().sleeper(3000);
                // Wait and make sure that the trade executed. If not, keep waiting
                List<Order> openOrders = client.getOpenOrders(new OrderRequest(ticker));
                openOrders.removeIf(i -> i.getSide().name().equals(OrderSide.BUY.name()));
                while (!openOrders.isEmpty()) {
                    loggerUtil.info("Orders for " + ticker + " are not empty, waiting 3 seconds...");
                    new CalcUtils().sleeper(3000);
                    openOrders = client.getOpenOrders(new OrderRequest(ticker));
                    openOrders.removeIf(i -> i.getSide().name().equals(OrderSide.BUY.name()));
                }
                loggerUtil.info("Sell Trade executed successfully");
                loggerUtil.notify(ticker, "Sell Trade executed successfully");
                new CalcUtils().sleeper(3000);
            } catch (Exception e) {
                loggerUtil.error("There was an exception thrown during the sell?: " + e.getMessage());
                if (!e.getMessage().contains("MIN_NOTIONAL")) {
                    throw new Exception(e);
                } else {
                    loggerUtil.notify(ticker, "Skipping MIN_NOTIONAL error during sell for now - " + e.getMessage());
                }
            }
        } else {
            message = "Cannot sell " + freeCoinFloored + cryptoCoin;
            loggerUtil.info(message);
            //loggerUtil.notify(ticker, message);
        }
        /**
         * Limit BUY method starts
         */
        //TODO: refactor this method
        // Verify that we have the correct amount of asset to trade
        if (predictionEngine.getTradeSignal().equalsIgnoreCase("SELL")
                || predictionEngine.getTradeSignal().equalsIgnoreCase("RISK BUY")
                || stopLossSignal) {
            message = "Trade Signal is SELL or RISK BUY. So skipping BuyBack for this iteration";
            loggerUtil.info(message);
            //loggerUtil.notify(ticker, message);
            return;
        }
        //Commenting this logic for now. Lets see how RISK BUY SIGNAL performs and decide on this
        /**
         *else if(predictionEngine.getCurrentPrice() < lastTargetPrice) {
         *             message = "predictionEngine.getCurrentPrice() < lastTargetPrice. So skipping BuyBack for this iteration";
         *             loggerUtil.info(message);
         *             telegramUtil.sendToTelegram(ticker, message);
         *             return;
         *         }
         */

        account = client.getAccount();
        Double totalBalance = getTotalBalanceInUSDT();
        Double coinAllocatePercent = env.getProperty("trading.bot." + ticker + ".percentAllocate", Double.class);
        if(coinAllocatePercent == null) {
            coinAllocatePercent = PredictionEngine.altCoinAllocatePercent;
        }
        //Double coinAllocatePercent = calculateAllocatePercentBasedOnVolume(ticker);
        Double allocateValueForthisCoinInUSDT = (totalBalance * coinAllocatePercent) / 100;
        Double freeUSDT = Double.valueOf(account.getAssetBalance("USDT").getFree());
        balance = account.getAssetBalance(cryptoCoin);
        sellableAmount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());

        Double minQty = Double.valueOf(symbolFilters.stream().filter(i -> i.getFilterType().name().equals(FilterType.LOT_SIZE.name()))
                .findFirst().get().getMinQty());

        if (sellableAmount > minQty || freeUSDT < allocateValueForthisCoinInUSDT) {
            message = "Cannot buy";
            loggerUtil.info(message);
            //loggerUtil.notify(ticker, message);
            return;
        }
        // Calculate and round the values in preparation for buying back
        Double allocateValueInUSDTFloored = CalcUtils.floorTo(allocateValueForthisCoinInUSDT, 2);
        Double coinToBuyFloored = CalcUtils.floorTo(allocateValueInUSDTFloored / buyPrice, String.valueOf(buyPrice.intValue()).length() - 1);

        Double minNotional = Double.valueOf(symbolFilters.stream().filter(i -> i.getFilterType().name().equals(FilterType.MIN_NOTIONAL.name()))
                .findFirst().get().getMinNotional());
        if ((coinToBuyFloored * buyPrice) < minNotional || buyPrice < minPrice) {
            message = "coinToBuyFloored : " + coinToBuyFloored + "; buyPrice : " + buyPrice + "; minPrice : " + minPrice
                     + "; minNotional"  + minNotional
                    + "; (coinToBuyFloored * buyPrice) < minNotional || buyPrice < minPrice. Cannot buy";
            loggerUtil.info(message);
            //loggerUtil.notify(ticker, message);
            return;
        }
        try {
            //Cancelling last open LIMIT BUY Orders
            List<Order> limitBuyOrders = client.getOpenOrders(new OrderRequest(ticker))
                    .stream().filter(i -> i.getSide().name().equalsIgnoreCase("BUY")
                            && i.getType().name().equalsIgnoreCase("LIMIT")).collect(Collectors.toList());
            if (!limitBuyOrders.isEmpty()) {
                limitBuyOrders.forEach(order -> {
                    loggerUtil.info("Cancelling order: " + order.getOrderId());
                    client.cancelOrder(new CancelOrderRequest(ticker, order.getOrderId()));
                });
            }

            buyPrice = CalcUtils.floorTo(buyPrice, floorDecimal);

            //Send SMA graph before limit buy
            movingAverageChartHelper.sendTelegramMessage(ticker, predictionEngine.getjFreeChart());
            loggerUtil.info(
                    "Executing buy with: "
                            + allocateValueInUSDTFloored
                            + " USDT @ $"
                            + df.format(buyPrice)
                            + "="
                            + coinToBuyFloored
                            + " " + cryptoCoin);
            loggerUtil.notify(ticker, "Executing buy with: "
                    + allocateValueInUSDTFloored
                    + " USDT @ $"
                    + df.format(buyPrice)
                    + "="
                    + coinToBuyFloored
                    + " " + cryptoCoin);
            // Submit the Binance buy back
            NewOrderResponse performBuy =
                    client.newOrder(
                            limitBuy(
                                    ticker,
                                    TimeInForce.GTC,
                                    coinToBuyFloored.toString(),
                                    df.format(buyPrice)));
            loggerUtil.info("Trade submitted: " + performBuy.getTransactTime());
            loggerUtil.notify(ticker, "Limit Buy submitted");
        } catch (Exception e) {
            loggerUtil.error("There was an exception thrown during the buy?: " + e.getMessage());
            throw new Exception(e);
        }
        /**
         * Limit BUY method ends
         */
        new CalcUtils().sleeper(3000);
    }

    public static void main(String[] args) {
        double i = 35.0 / 100;
        System.out.println(i);
    }

    /**
     * Execute a market buy back
     */
    private boolean executeMarketBuyBack(String ticker) {
        String message = "";
        String cryptoCoin = ticker.replace("USDT", "");
        // Cancel all open orders
        List<Order> openOrders = client.getOpenOrders(new OrderRequest(ticker));
        for (Order order : openOrders) {
            loggerUtil.info("Cancelling order: " + order.getOrderId());
            client.cancelOrder(new CancelOrderRequest(ticker, order.getOrderId()));
        }
        // Execute market buy back
        new CalcUtils().sleeper(3000);
        Account account = client.getAccount();

        Double totalBalance = getTotalBalanceInUSDT();
        // Find out how much free asset there is to trade
        Double coinAllocatePercent = env.getProperty("trading.bot." + ticker + ".percentAllocate", Double.class);
        //Double coinAllocatePercent = calculateAllocatePercentBasedOnVolume(ticker);
        if(coinAllocatePercent == null) {
            coinAllocatePercent = 500.00 / totalBalance;
        }
        Double allocateValueForThisCoinInUSDT = (totalBalance * coinAllocatePercent) / 100;
        Double freeUSDT = Double.valueOf(account.getAssetBalance("USDT").getFree());

        AssetBalance assetBalance = account.getAssetBalance(cryptoCoin);
        Double amount = Double.valueOf(assetBalance.getFree()) + Double.valueOf(assetBalance.getLocked());

        if (amount > 0.0 && freeUSDT < allocateValueForThisCoinInUSDT) {
            message = "Cannot execute market buy";
            loggerUtil.info(message);
            loggerUtil.notify(ticker, message);
            return false;
        }
        Double lastPrice = getCurrentPrice(ticker);
        Double coinToBuyFloored = CalcUtils.floorTo(allocateValueForThisCoinInUSDT / lastPrice, String.valueOf(lastPrice.intValue()).length() - 1);
        message = "Executing market buy back of " + coinToBuyFloored + " " + cryptoCoin + " @ $" + lastPrice;
        loggerUtil.info(message);
        loggerUtil.notify(ticker, message);
        client.newOrder(marketBuy(ticker, coinToBuyFloored.toString()));
        new CalcUtils().sleeper(15000);
        message = "market buy back submitted successfully";
        loggerUtil.notify(ticker, message);
        return true;
    }

    private Double calculateAllocatePercentBasedOnVolume(String ticker) {
        TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker);
        Double ticker24hVolumeInUSDT = Double.valueOf(tickerStatistics.getVolume()) * Double.valueOf(tickerStatistics.getLastPrice());
        tickerStatistics = client.get24HrPriceStatistics("BTCUSDT");
        Double btc24hVolumeInUSDT = Double.valueOf(tickerStatistics.getVolume()) * Double.valueOf(tickerStatistics.getLastPrice());
        if (ticker24hVolumeInUSDT > btc24hVolumeInUSDT) {
            loggerUtil.notify(ticker, "Ticker volume > BTC volume. very unreal. Check this!");
            return 0.0;
        }
        return CalcUtils.roundTo((ticker24hVolumeInUSDT / btc24hVolumeInUSDT) * 100, 2);
    }

    /**
     * Report that the system is in developer mode
     */
    private void reportDevMode() {
        loggerUtil.debug("Bot is currently in development mode! Not performing trades");
    }

    public PredictionEngine predictTrendAndDecide(String ticker, List<Candlestick> candlesticks) {
        PredictionEngine predictionEngine = new PredictionEngine();
        //Get JFreeChart
        JFreeChart jFreeChart = movingAverageChartHelper.getChart(ticker, candlesticks);
        //Send chart to telegram bot group
        predictionEngine.setjFreeChart(jFreeChart);
        TimeSeriesCollection timeSeriesCollection = (TimeSeriesCollection) jFreeChart.getXYPlot().getDataset();
        TimeSeries liveTimeSeries = timeSeriesCollection.getSeries(0);
        TimeSeries shortTermSeries = timeSeriesCollection.getSeries(1);
        TimeSeries longTermSeries = timeSeriesCollection.getSeries(2);
        TimeSeriesDataItem liveTermSeriesDataItem = liveTimeSeries.getDataItem(liveTimeSeries.getItemCount() - 1);
        TimeSeriesDataItem shortTermSeriesDataItem = shortTermSeries.getDataItem(shortTermSeries.getItemCount() - 1);
        TimeSeriesDataItem longTermSeriesDataItem = longTermSeries.getDataItem(longTermSeries.getItemCount() - 1);
        //Check shortTerm crosses longTerm graph
        StringBuilder messageBuilder = new StringBuilder();
        if (shortTermSeriesDataItem.getPeriod().getEnd().equals(longTermSeriesDataItem.getPeriod().getEnd())) {
            Double shortSMAValue = new BigDecimal(shortTermSeriesDataItem.getValue().doubleValue()).setScale(8, RoundingMode.HALF_UP).doubleValue();
            Double longSMAValue = new BigDecimal(longTermSeriesDataItem.getValue().doubleValue()).setScale(8, RoundingMode.HALF_UP).doubleValue();
            Double currentValue = new BigDecimal(liveTermSeriesDataItem.getValue().doubleValue()).setScale(8, RoundingMode.HALF_UP).doubleValue();
            predictionEngine.setCurrentPrice(currentValue);
            predictionEngine.setLastShortTermPrice(shortSMAValue);
            messageBuilder.append("shortTermMA").append(":").append(shortSMAValue).append("; ")
                    .append("longTermMA").append(":").append(longSMAValue).append("; ")
                    .append("CurrentPrice").append(":").append(currentValue)
                    .append("; ");
            //loggerUtil.notify(ticker, messageBuilder.toString());
            loggerUtil.info(messageBuilder.toString());
            messageBuilder = new StringBuilder();
            if (shortSMAValue == longSMAValue) {
                messageBuilder.append("_________GOLDEN CROSS_________").append("shortTermSMA equals longTermSMA")
                        .append("Keep watching this trend. It might either be BUY or SELL signal in future").append("; ");
            } else if (shortSMAValue > longSMAValue) {
                messageBuilder.append("shortTermMA").append(">").append("longTermMA")
                        .append("; ");
                if (currentValue >= (shortSMAValue * PredictionEngine.sellPriceMultiplier)) {
                    messageBuilder.append("CurrentPrice").append(">=").append("(shortSMAValue * AvgPredictionEngine.sellPriceMultiplier)").append("; ")
                            .append("GOOD BUY SIGNAL").append("; ");
                    predictionEngine.setTradeSignal("GOOD BUY");
                } else {
                    messageBuilder.append("CurrentPrice").append("<").append("shortSMAValue").append("; ")
                            .append("RISK BUY SIGNAL").append("; ");
                    predictionEngine.setTradeSignal("RISK BUY");
                }
            } else if (shortSMAValue <= longSMAValue) {
                messageBuilder.append("shortTermMA").append("<=").append("longTermMA")
                        .append("; ");
                if (currentValue >= shortSMAValue) {
                    messageBuilder
                            .append("CurrentPrice").append(">=").append("shortSMAValue").append("; ")
                            .append("Don't take this Risk. you will lose in long run").append("; ")
                            .append("SELL SIGNAL").append("; ");
                    predictionEngine.setTradeSignal("SELL");
                } else {
                    messageBuilder.append("currentValue").append("<").append("shortSMAValue")
                            .append("; ").append("SELL SIGNAL").append("; ");
                    predictionEngine.setTradeSignal("SELL");
                }
            }
            loggerUtil.info(messageBuilder.toString());
            //loggerUtil.notify(ticker, messageBuilder.toString());
        }
        return predictionEngine;
    }
}