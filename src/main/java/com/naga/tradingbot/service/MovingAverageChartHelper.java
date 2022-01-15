package com.naga.tradingbot.service;

import com.binance.api.client.domain.market.Candlestick;
import com.naga.tradingbot.utils.LoggerUtil;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A demo showing a high-low-open-close chart with a moving average overlaid on top.
 */
@Service
public class MovingAverageChartHelper {

    @Value("${trading.bot.shortTermMA}")
    private Integer shortTermMA;

    @Value("${trading.bot.longTermMA}")
    private Integer longTermMA;

    @Value("${trading.bot.strategy}")
    private String maStrategy;

    @Autowired
    private LoggerUtil loggerUtil;

    /**
     * A demonstration application showing a high-low-open-close chart.
     *
     * @param candlesticks
     */
    public JFreeChart getChart(String ticker, List<Candlestick> candlesticks) {

        final JFreeChart chart = createChart(ticker, candlesticks);
        final ChartPanel chartPanel = new ChartPanel(chart);

        return chart;
    }

    public boolean sendTelegramMessage(String ticker, JFreeChart chart) throws Exception {
        try {
            ChartUtils.saveChartAsJPEG(new File(ticker + "_SMA_Chart.jpeg"), chart, 2000, 2000);
        } catch (IOException e) {
            throw new Exception(e);
        }
        return loggerUtil.sendPhoto(ticker, new File(ticker + "_SMA_Chart.jpeg"));
    }


    public TimeSeries createTimeSeries(String ticker, List<Candlestick> candlesticks) {
        TimeSeries timeSeries = new TimeSeries(ticker);

        for (Candlestick candlestick : candlesticks) {
            RegularTimePeriod regularTimePeriod = RegularTimePeriod.createInstance(Hour.class, new Date(candlestick.getCloseTime()), TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
            timeSeries.addOrUpdate(new TimeSeriesDataItem(regularTimePeriod, Double.parseDouble(candlestick.getClose())));
        }
        return timeSeries;
    }

    /**
     * Creates a sample chart.
     *
     * @param candlesticks
     * @return a sample chart.
     */
    private JFreeChart createChart(String ticker, List<Candlestick> candlesticks) {

        //current price
        TimeSeries dataset1 = createTimeSeries(ticker, candlesticks);

        //7SMA
        final TimeSeries dataset2 = MovingAverage.createMovingAverage(
                dataset1, shortTermMA + maStrategy, shortTermMA * 24, 0
        );

        //20SMA
        final TimeSeries dataset3 = MovingAverage.createMovingAverage(
                dataset1, longTermMA + maStrategy, longTermMA * 24, 0
        );

        TimeSeriesCollection timeSeriesCollection = new TimeSeriesCollection();
        timeSeriesCollection.addSeries(dataset1);
        timeSeriesCollection.addSeries(dataset2);
        timeSeriesCollection.addSeries(dataset3);

        final JFreeChart chart = ChartFactory.createTimeSeriesChart(
                ticker + " Time Series",
                "Time",
                "Value",
                timeSeriesCollection,
                true,
                true,
                true
        );

        final DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
        axis.setTickMarkPosition(DateTickMarkPosition.START);

        final XYPlot plot = (XYPlot) chart.getPlot();
        //plot.setDataset(1, timeSeriesCollection);
        plot.mapDatasetToRangeAxis(0, 0);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        plot.setRenderer(renderer);
        renderer.setSeriesPaint(0, new Color(224, 88, 119));
        renderer.setSeriesPaint(1, new Color(8, 9, 8));
        renderer.setSeriesPaint(2, new Color(35, 54, 149));
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesShapesVisible(2, false);
        /*plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        ((AbstractRenderer) plot.getRenderer(0)).setAutoPopulateSeriesStroke(false);
        plot.getRenderer(0).setSeriesStroke(0, new BasicStroke(0.02f));
        plot.getRenderer(0).setSeriesStroke(1, new BasicStroke(0.02f));
        plot.getRenderer(0).setSeriesStroke(2, new BasicStroke(0.02f));*/


        return chart;

    }
}
