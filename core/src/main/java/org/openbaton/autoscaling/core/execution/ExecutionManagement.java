package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
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

    private Map<String, Set<ScheduledFuture>> tasks;

    @Autowired
    private VnfrMonitor vnfrMonitor;

    private NFVORequestor nfvoRequestor;

    private Properties properties;

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
    }

    public void activate(NetworkServiceRecord nsr) throws NotFoundException {
        log.debug("==========ACTIVATE============");
        log.debug(properties.toString());
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            if (vnfr.getAuto_scale_policy().size() > 0)
                activate(vnfr);
        }
    }

    public void activate(VirtualNetworkFunctionRecord vnfr) throws NotFoundException {
        log.debug("Activating Elasticity for VNFR " + vnfr.getId());
        vnfrMonitor.addVnfr(vnfr.getId());
        log.debug("=======VNFR-MONITOR=======");
        log.debug(vnfrMonitor.toString());
        if (!tasks.containsKey(vnfr.getId())) {
            log.debug("Creating new ElasticityTasks for VNFR with id: " + vnfr.getId());
            tasks.put(vnfr.getId(), new HashSet<ScheduledFuture>());
            for (AutoScalePolicy policy : vnfr.getAuto_scale_policy()) {
                log.debug("Creating new ElasticityTask for AutoScalingPolicy " + policy.getAction() + " with id: " + policy.getId() + " of VNFR with id: " + vnfr.getId());
//                ElasticityTask elasticityTask = (ElasticityTask) context.getBean("elasticityTask");
                DetectionTask detectionTask = new DetectionTask();
                detectionTask.init(vnfr, policy, vnfrMonitor, properties);
                ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(detectionTask, policy.getPeriod() * 1000);
                tasks.get(vnfr.getId()).add(scheduledFuture);
            }
            log.debug("Activated Elasticity for VNFR " + vnfr.getId());
        } else {
            log.debug("ElasticityTasks for VNFR with id " + vnfr.getId() + " were already activated");
        }
    }

    public void deactivate(NetworkServiceRecord nsr) {
        log.debug("Deactivating Elasticity for all VNFRs of NSR with id: " + nsr.getId());
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
                deactivate(vnfr);
        }
        log.debug("Deactivated Elasticity for all VNFRs of NSR with id: " + nsr.getId());
    }

    public void deactivate(VirtualNetworkFunctionRecord vnfr) {
        log.debug("Deactivating Elasticity for VNFR " + vnfr.getId());
        if (tasks.containsKey(vnfr.getId())) {
            Set<ScheduledFuture> vnfrTasks = tasks.get(vnfr.getId());
            for (ScheduledFuture scheduledFuture : vnfrTasks) {
                scheduledFuture.cancel(false);
            }
            tasks.remove(vnfr.getId());
            log.debug("Deactivated Elasticity for VNFR " + vnfr.getId());
        } else {
            log.debug("Not Found any ElasticityTasks for VNFR with id: " + vnfr.getId());
        }
        vnfrMonitor.removeVnfr(vnfr.getId());
    }
}
