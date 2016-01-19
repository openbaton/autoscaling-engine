package org.openbaton.autoscaling.core.decision;

import org.openbaton.autoscaling.core.decision.task.DecisionTask;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DecisionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> tasks;

    private Properties properties;

    @Autowired
    private DecisionEngine decisionEngine;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
    }

    public void decide(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
        log.debug("Processing decision request of AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        if (tasks.get(vnfr_id) == null) {
            log.debug("Creating new DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
            DecisionTask decisionTask = new DecisionTask(nsr_id, vnfr_id, autoScalePolicy, properties, decisionEngine);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(decisionTask, new Date());
            tasks.put(vnfr_id, scheduledFuture);
        } else {
            log.debug("Processing already a decision request for this VNFR. Cannot create another DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        }
    }

    public void finished(String vnfr_id) {
        log.debug("Finished Decision request of VNFR with id " + vnfr_id + " of VNFR with id: " + vnfr_id);
        tasks.remove(vnfr_id);
    }

}
