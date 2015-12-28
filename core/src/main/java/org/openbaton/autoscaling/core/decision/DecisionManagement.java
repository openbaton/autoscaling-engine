package org.openbaton.autoscaling.core.decision;

import org.openbaton.autoscaling.core.decision.task.DecisionTask;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

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
    private ExecutionManagement executionManagement;

    //@PostConstruct
    public void init(Properties properties) {
        log.debug("======================");
        log.debug(properties.toString());
        this.properties = properties;
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
    }

    public void decide(String vnfr_id, AutoScalePolicy autoScalePolicy) {
        log.debug("Processing decision request of AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        if (tasks.get(autoScalePolicy.getId()) == null) {
            log.debug("Creating new DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
            DecisionTask decisionTask = new DecisionTask(vnfr_id, autoScalePolicy, properties);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(decisionTask, new Date());
            tasks.put(autoScalePolicy.getId(), scheduledFuture);
        } else {
            log.debug("Processing already a decision request for this AutoScalePolicy. Cannot create another DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        }
    }

    public void finished(String vnfr_id, AutoScalePolicy autoScalePolicy) {
        log.debug("Finished Decision request of AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        tasks.remove(autoScalePolicy.getId());
    }

    public void sendToExecutor(String vnfr_id, Set<ScalingAction> actions) {
        executionManagement.execute(vnfr_id, actions);
    }

}
