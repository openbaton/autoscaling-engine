package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.execution.task.CooldownTask;
import org.openbaton.autoscaling.core.execution.task.ExecutionTask;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ExecutionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> executionTasks;

    private Map<String, ScheduledFuture> cooldownTasks;

    private Set<String> terminatingTasks;

    private Properties properties;

    @Autowired
    private ExecutionEngine executionEngine;

    private NFVORequestor nfvoRequestor;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("nfvo.username"), properties.getProperty("nfvo.password"), properties.getProperty("nfvo.ip"), properties.getProperty("nfvo.port"), "1");
        this.executionTasks = new HashMap<>();
        this.cooldownTasks = new HashMap<>();
        this.terminatingTasks = new HashSet<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setRemoveOnCancelPolicy(true);
        this.taskScheduler.initialize();
        //executionEngine = new ExecutionEngine(properties);
    }

    public void executeActions(String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown) {
        log.debug("Processing execution request of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
        if (!executionTasks.containsKey(vnfr_id) && !cooldownTasks.containsKey(vnfr_id)) {
            log.debug("Creating new ExecutionTask of ScalingActions: " + actions + " for VNFR with id: " + vnfr_id);
            ExecutionTask executionTask = new ExecutionTask(nsr_id, vnfr_id, actions, cooldown, properties, executionEngine);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(executionTask, new Date());
            executionTasks.put(vnfr_id, scheduledFuture);
        } else {
            if (executionTasks.containsKey(vnfr_id)) {
                log.debug("Processing already an execution request for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            } else if (cooldownTasks.containsKey(vnfr_id)) {
                log.debug("Waiting for Cooldown for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            }
        }
    }

    public void executeCooldown(String nsr_id, String vnfr_id, long cooldown) {
        log.debug("Processing CooldownTask for VNFR with id: " + vnfr_id);
        if (!executionTasks.containsKey(vnfr_id) && !cooldownTasks.containsKey(vnfr_id)) {
            log.debug("Creating new CooldownTask for VNFR with id: " + vnfr_id);
            CooldownTask cooldownTask = new CooldownTask(nsr_id, vnfr_id, cooldown, properties, executionEngine);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(cooldownTask, new Date());
            cooldownTasks.put(vnfr_id, scheduledFuture);
        } else {
            if (executionTasks.containsKey(vnfr_id)) {
                log.debug("Processing already an execution request for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            } else if (cooldownTasks.containsKey(vnfr_id)) {
                log.debug("Waiting for Cooldown for VNFR with id: " + vnfr_id + ". Cannot create another ExecutionTask for VNFR with id: " + vnfr_id);
            }
        }
    }

    public void finishedScaling(String vnfr_id) {
        if (executionTasks.containsKey(vnfr_id)) {
            log.debug("Finished execution of Actions for VNFR with id: " + vnfr_id);
            executionTasks.remove(vnfr_id);
        }
    }

    public void finishedExecution(String vnfr_id) {
        if (executionTasks.containsKey(vnfr_id)) {
            log.debug("Finished execution of Actions for VNFR with id: " + vnfr_id);
            executionTasks.remove(vnfr_id);
        }
    }

    public void finishedCooldown(String nsr_id, String vnfr_id) {
        if (cooldownTasks.containsKey(vnfr_id)) {
            log.debug("Finished Cooldown for VNFR with id: " + vnfr_id);
            cooldownTasks.remove(vnfr_id);
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
    }

    public void stop(String nsr_id, String vnfr_id) {
        log.debug("Stopping ExecutionTask/CooldownTask for VNFR with id: " + vnfr_id);
        if (executionTasks.containsKey(vnfr_id)) {
            //tasks.get(vnfr_id).cancel(false);
            //ScheduledFuture<ExecutionTask> task = tasks.get(vnfr_id);
            terminate(nsr_id, vnfr_id);
            while (executionTasks.containsKey(vnfr_id)) {
                log.debug("Waiting for finishing ExecutionTask for VNFR with id: " + vnfr_id);
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
            log.debug("Stopped ExecutionTask for VNFR with id: " + vnfr_id);
        } else {
            log.debug("No ExecutionTask was running for VNFR with id: " + vnfr_id);
        }
        if (cooldownTasks.containsKey(vnfr_id)) {
            while (cooldownTasks.containsKey(vnfr_id)) {
                log.debug("Waiting for finishing CooldownTask for VNFR with id: " + vnfr_id);
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
            log.debug("Stopped CooldownTask for VNFR with id: " + vnfr_id);
        } else {
            log.debug("No CooldownTask was running for VNFR with id: " + vnfr_id);
        }
    }

    public void terminate(String nsr_id, String vnfr_id) {
        if (executionTasks.containsKey(vnfr_id) || cooldownTasks.containsKey(vnfr_id)) {
            terminatingTasks.add(vnfr_id);

        }
    }

    public boolean isTerminating(String vnfr_id) {
        if (terminatingTasks.contains(vnfr_id)) {
            return true;
        } else {
            return false;
        }
    }

    public void terminated(String vnfr_id) {
        if (executionTasks.containsKey(vnfr_id)) {
            executionTasks.remove(vnfr_id);
            terminatingTasks.remove(vnfr_id);
        }
    }

}
