package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.execution.task.ExecutionTask;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.utils.Utils;
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
public class ExecutionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> tasks;

    @Autowired
    private VnfrMonitor vnfrMonitor;

    private Properties properties;

    @Autowired
    private ExecutionEngine executionEngine;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        //this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
        //executionEngine = new ExecutionEngine(properties);
    }

    public void execute(String nsr_id, String vnfr_id, Set<ScalingAction> actions, long timeout) {
        log.debug("Processing execution request of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
        if (tasks.get(vnfr_id) == null) {
            log.debug("Creating new ExecutionTask of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
            ExecutionTask executionTask = new ExecutionTask(nsr_id, vnfr_id, actions, timeout, properties, executionEngine);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(executionTask, new Date());
            tasks.put(vnfr_id, scheduledFuture);
        } else {
            log.debug("Processing already an execution request for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
        }
    }

    public void finish(String vnfr_id) {
        if (tasks.containsKey(vnfr_id)) {
            log.debug("Finished execution of Actions for VNFR with id: " + vnfr_id);
            tasks.remove(vnfr_id);
        }
    }
}
