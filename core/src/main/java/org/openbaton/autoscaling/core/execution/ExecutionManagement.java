package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.execution.task.ExecutionTask;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
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
public class ExecutionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> tasks;

    @Autowired
    private VnfrMonitor vnfrMonitor;

    private NFVORequestor nfvoRequestor;

    private Properties properties;

    private ExecutionEngine executionEngine;

//    @PostConstruct
    public void init(Properties properties) {
        log.debug("======================");
        log.debug(properties.toString());
        this.properties = properties;
        //this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
        executionEngine = new ExecutionEngine(properties);
    }

    public void execute(String vnfr_id, Set<ScalingAction> actions) {
        log.debug("Processing execution request of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
        if (tasks.get(vnfr_id) == null) {
            log.debug("Creating new ExecutionTask of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
            ExecutionTask executionTask = new ExecutionTask(vnfr_id, actions, properties);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(executionTask, new Date());
            tasks.put(vnfr_id, scheduledFuture);
        } else {
            log.debug("Processing already an exection request for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
        }
    }
}
