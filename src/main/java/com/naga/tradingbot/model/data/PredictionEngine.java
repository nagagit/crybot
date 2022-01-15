package com.naga.tradingbot.model.data;

import com.naga.tradingbot.utils.CalcUtils;
import org.jfree.chart.JFreeChart;

public class PredictionEngine {
  public static Double buyBackAfterThisPercentage = 0.990;
  public static Double sellPriceMultiplier = 1.018;
  public static Double altCoinAllocatePercent = 2.5;
  private Double targetPrice;
  private Double lastShortTermPrice;
  private Double currentPrice;
  private String tradeSignal = "DONOTHING";
  private JFreeChart jFreeChart;

  public JFreeChart getjFreeChart() {
    return jFreeChart;
  }

  public void setjFreeChart(JFreeChart jFreeChart) {
    this.jFreeChart = jFreeChart;
  }

  public String getTradeSignal() {
    return tradeSignal;
  }

  public void setTradeSignal(String tradeSignal) {
    this.tradeSignal = tradeSignal;
  }


  public Double getTargetPrice() {
    return targetPrice;
  }

  public void setTargetPrice(Double targetPrice) {
    this.targetPrice = targetPrice;
  }

  public Double getLastShortTermPrice() {
    return lastShortTermPrice;
  }

  public void setLastShortTermPrice(Double lastShortTermPrice) {
    this.lastShortTermPrice = lastShortTermPrice;
  }

  public Double getCurrentPrice() {
    return currentPrice;
  }

  public void setCurrentPrice(Double currentPrice) {
    this.currentPrice = currentPrice;
  }


  /** PredictionEngine constructor */
  public PredictionEngine() {
  }

  /**
   * Use mind data to find averages and then predict a target price to sell at.
   *
   */
  public void executeThoughtProcess() {
    // Calculate target price by maxing the targetPrices and add a small percentage
    setTargetPrice(CalcUtils.floorTo(lastShortTermPrice * sellPriceMultiplier, 8));
  }
}
