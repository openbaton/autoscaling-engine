package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.execution.task.CooldownTask;
import org.openbaton.autoscaling.core.execution.task.ExecutionTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
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

    private Properties properties;

    @Autowired
    private ExecutionEngine executionEngine;

    private NFVORequestor nfvoRequestor;

    private ActionMonitor actionMonitor;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.actionMonitor = new ActionMonitor();
        this.executionEngine.setActionMonitor(actionMonitor);
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("nfvo.username"), properties.getProperty("nfvo.password"), properties.getProperty("nfvo.ip"), properties.getProperty("nfvo.port"), "1");
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setRemoveOnCancelPolicy(true);
        this.taskScheduler.initialize();
    }

    public void executeActions(String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown) {
        log.debug("Processing execution request of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
        if (actionMonitor.requestAction(vnfr_id, Action.SCALE)) {
            log.debug("Creating new ExecutionTask of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
            ExecutionTask executionTask = new ExecutionTask(nsr_id, vnfr_id, actions, cooldown, properties, executionEngine, actionMonitor);
            taskScheduler.execute(executionTask);
        } else {
            if (actionMonitor.getAction(vnfr_id) == Action.SCALE) {
                log.debug("Processing already an execution request for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            } else if (actionMonitor.getAction(vnfr_id) == Action.COOLDOWN) {
                log.debug("Waiting for Cooldown for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            } else {
                log.warn("Problem while starting ExecutionThread. Internal Status is: " + actionMonitor.getAction(vnfr_id));
            }
        }
    }

    public void executeCooldown(String nsr_id, String vnfr_id, long cooldown) {
        if (actionMonitor.isTerminating(vnfr_id)) {
            actionMonitor.finishedAction(vnfr_id, Action.TERMINATED);
            return;
        }
        log.debug("Starting CooldownTask for VNFR with id: " + vnfr_id);
        if (actionMonitor.requestAction(vnfr_id, Action.COOLDOWN)) {
            log.debug("Creating new CooldownTask for VNFR with id: " + vnfr_id);
            CooldownTask cooldownTask = new CooldownTask(nsr_id, vnfr_id, cooldown, properties, executionEngine, actionMonitor);
            taskScheduler.execute(cooldownTask);
        } else {
            if (actionMonitor.getAction(vnfr_id) == Action.COOLDOWN) {
                log.debug("Waiting already for Cooldown for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            }
        }
    }

    public void stop(String nsr_id) {
        log.debug("Stopping ExecutionTask for all VNFRs of NSR with id: " + nsr_id);
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (nsr != null && nsr.getVnfr() != null) {
            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
                stop(nsr_id, vnfr.getId());
            }
        }
        log.debug("Stopped all ExecutionTasks for NSR with id: " + nsr_id);
    }

    public void stop(String nsr_id, String vnfr_id) {
        log.debug("Stopping ExecutionTask/CooldownTask for VNFR with id: " + vnfr_id);
        while (!actionMonitor.isTerminated(vnfr_id) && actionMonitor.getAction(vnfr_id) != Action.INACTIVE) {
            actionMonitor.terminate(vnfr_id);
            log.debug("Waiting for finishing ExecutionTask/Cooldown for VNFR with id: " + vnfr_id);
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
        actionMonitor.removeId(vnfr_id);
        log.debug("Stopped ExecutionTask for VNFR with id: " + vnfr_id);
    }
}
