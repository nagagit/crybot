/*
package com.naga.tradingbot.service;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

@Service
public class JobScheduler {

    @Autowired
    private Environment env;

    @Autowired
    private Scheduler quartzScheduler;

    @PostConstruct
    public void scheduleIndividualCoins() throws SchedulerException {
        if (!env.getProperty("developmentMode", String.class).equals("true")) {
            for (String ticker : env.getProperty("trading.bot.scheduledTickers", String.class).split(",")) {
                String cron = env.getProperty("trading.bot." + ticker + ".cron", String.class);
                if (!StringUtils.isEmpty(cron)) {
                    JobDetail jobDetail = JobBuilder.newJob(BotExecuteEachCoinJob.class)
                            .withIdentity("BotExecuteEachCoinJob.class" + "_" + ticker, "BotExecuteEachCoinJob.class" + "_" + ticker + "_Group")
                            .usingJobData("ticker", ticker)
                            .build();
                    Trigger trigger = TriggerBuilder.newTrigger().withIdentity("BotExecuteEachCoinJob.class" + "_" + ticker, "BotExecuteEachCoinJob.class" + "_" + ticker + "_Group")
                            .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build();
                    try {
                        quartzScheduler.scheduleJob(jobDetail, trigger);
                    } catch (ObjectAlreadyExistsException e) {
                        quartzScheduler.deleteJob(jobDetail.getKey());
                        quartzScheduler.scheduleJob(jobDetail, trigger);
                    }
                }
            }
            if (!quartzScheduler.isStarted()) {
                quartzScheduler.start();
            }
        }
    }

    @PostConstruct
    public void scheduleAllCoins() throws SchedulerException {
        if (!env.getProperty("developmentMode", String.class).equals("true")) {
            String cron = env.getProperty("trading.bot.cron", String.class);
            if (!StringUtils.isEmpty(cron)) {
                JobDetail jobDetail = JobBuilder.newJob(BotExecuteJob.class)
                        .withIdentity("BotExecuteJob.class", "BotExecuteJob.class_Group")
                        .build();
                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity("BotExecuteJob.class", "BotExecuteJob.class_Group")
                        .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build();
                try {
                    quartzScheduler.scheduleJob(jobDetail, trigger);
                } catch (ObjectAlreadyExistsException e) {
                    quartzScheduler.deleteJob(jobDetail.getKey());
                    quartzScheduler.scheduleJob(jobDetail, trigger);
                }
            }
        }
        if (!quartzScheduler.isStarted()) {
            quartzScheduler.start();
        }
    }
}
*/
