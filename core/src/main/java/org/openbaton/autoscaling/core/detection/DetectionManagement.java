package org.openbaton.autoscaling.core.detection;

import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdDetails;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdType;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;


import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
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
public class DetectionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, Map<String, Map<String, ScheduledFuture>>> tasks;

    private Properties properties;

    private NFVORequestor nfvoRequestor;

    @Autowired
    private VnfrMonitor vnfrMonitor;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.nfvoRequestor = new NFVORequestor(this.properties.getProperty("openbaton-username"), this.properties.getProperty("openbaton-password"), this.properties.getProperty("openbaton-url"), this.properties.getProperty("openbaton-port"), "1");
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
    }

    public void activate(String nsr_id) throws NotFoundException {
        log.debug("Activating Alarm Detection for NSR with id: " + nsr_id);
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (nsr == null) {
            throw new NotFoundException("Not Found NetworkServiceDescriptor with id: " + nsr_id);
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
                activate(nsr_id, vnfr.getId(), autoScalePolicy);
            }
        }
        log.info("Activated Alarm Detection for NSR with id: " + nsr_id);
    }

    public void activate(String nsr_id, String vnfr_id) throws NotFoundException {
        log.debug("Activating Alarm Detection for VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr == null) {
            //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            return;
        }
        for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
                    activate(nsr_id, vnfr.getId(), autoScalePolicy);
        }
        log.debug("Activated Alarm Detection for VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
    }

    public void activate(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) throws NotFoundException {
        log.debug("Activating Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        if (!tasks.containsKey(nsr_id)) {
            tasks.put(nsr_id, new HashMap<String, Map<String, ScheduledFuture>>());
        }
        if (!tasks.get(nsr_id).containsKey(vnfr_id)) {
            tasks.get(nsr_id).put(vnfr_id, new HashMap<String, ScheduledFuture>());
        }
        if (!tasks.get(nsr_id).get(vnfr_id).containsKey(autoScalePolicy.getId())) {
            log.debug("Creating new DetectionTask for AutoScalingPolicy " + autoScalePolicy.getName() + " with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
            DetectionTask detectionTask = new DetectionTask(nsr_id, vnfr_id, autoScalePolicy, properties);
            ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(detectionTask, autoScalePolicy.getPeriod() * 1000);
            tasks.get(nsr_id).get(vnfr_id).put(autoScalePolicy.getId(), scheduledFuture);
            log.info("Activated Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        } else {
            log.debug("Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id + " were already activated");
        }
    }

    public void deactivate(String nsr_id) throws NotFoundException {
        log.debug("Deactivating Alarm Detection of NSR with id: " + nsr_id);
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (nsr == null) {
            throw new NotFoundException("Not Found NetworkServiceDescriptor with id: " + nsr_id);
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            deactivate(nsr_id, vnfr.getId());
        }
        log.info("Deactivated Alarm Detection of NSR with id: " + nsr_id);
    }

    public void deactivate(String nsr_id, String vnfr_id) throws NotFoundException {
        log.debug("Deactivating Alarm Detection of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr == null) {
            //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            return;
        }
        for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
            deactivate(nsr_id, vnfr_id, autoScalePolicy);
        }
        log.debug("Deactivated Alarm Detection for VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
    }

    public void deactivate(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
        log.debug("Deactivating Elasticity for VNFR " + vnfr_id);
        if (tasks.containsKey(nsr_id)) {
            if (tasks.get(nsr_id).containsKey(vnfr_id)) {
                if (tasks.get(nsr_id).get(vnfr_id).containsKey(autoScalePolicy.getId())) {
                    tasks.get(vnfr_id);
                    tasks.remove(vnfr_id);
                    log.debug("Deactivated Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
                } else {
                    log.debug("Not Found DetectionTask for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
                }
            } else {
                log.debug("Not Found any DetectionTasks for VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
            }
        } else {
            log.debug("Not Found any DetectionTasks for NSR with id: " + nsr_id);
        }
        vnfrMonitor.removeVnfr(vnfr_id);
        log.debug("Deactivated Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
    }
}
