package org.openbaton.autoscaling.core.decision;

import org.openbaton.autoscaling.core.decision.task.DecisionTask;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
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
public class DecisionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private Properties properties;

    @Autowired
    private DecisionEngine decisionEngine;

    private NFVORequestor nfvoRequestor;

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> decisionTasks;

    private Set<String> terminatingTasks;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("nfvo.username"), properties.getProperty("nfvo.password"), properties.getProperty("nfvo.ip"), properties.getProperty("nfvo.port"), "1");

        this.decisionTasks = new HashMap<>();
        this.terminatingTasks = new HashSet<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setRemoveOnCancelPolicy(true);
        this.taskScheduler.initialize();
    }

    public void decide(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
        log.debug("Processing decision request of AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        if (decisionTasks.get(vnfr_id) == null) {
            log.debug("Creating new DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
            DecisionTask decisionTask = new DecisionTask(nsr_id, vnfr_id, autoScalePolicy, properties, decisionEngine);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(decisionTask, new Date());
            decisionTasks.put(vnfr_id, scheduledFuture);
        } else {
            log.debug("Processing already a decision request for this VNFR. Cannot create another DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
        }
    }

    public void finished(String vnfr_id) {
        log.debug("Finished Decision request of VNFR with id " + vnfr_id + " of VNFR with id: " + vnfr_id);
        decisionTasks.remove(vnfr_id);
    }

    public void stop(String nsr_id) {
        log.debug("Stopping DecisionTask for all VNFRs of NSR with id: " + nsr_id);
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
        log.debug("Stopping DecisionTask for VNFR with id: " + vnfr_id);
        if (decisionTasks.containsKey(vnfr_id)) {
            terminate(nsr_id, vnfr_id);
            while (decisionTasks.containsKey(vnfr_id)) {
                log.debug("Waiting for finishing DecisionTask for VNFR with id: " + vnfr_id);
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
            log.debug("Stopped DecisionTask for VNFR with id: " + vnfr_id);
        } else {
            log.debug("No DecisionTask was running for VNFR with id: " + vnfr_id);
        }
    }

    public void terminate(String nsr_id, String vnfr_id) {
        if (decisionTasks.containsKey(vnfr_id)) {
            terminatingTasks.add(vnfr_id);

        }
    }

    public boolean isTerminating(String autoScalePolicyId) {
        if (terminatingTasks.contains(autoScalePolicyId)) {
            return true;
        } else {
            return false;
        }
    }

    public void terminated(String autoScalePolicyId) {
        if (decisionTasks.containsKey(autoScalePolicyId)) {
            decisionTasks.remove(autoScalePolicyId);
            terminatingTasks.remove(autoScalePolicyId);
        }
    }



}
