package org.openbaton.autoscaling.core.detection.task;

import org.openbaton.autoscaling.catalogue.ScalingStatus;
import org.openbaton.autoscaling.core.detection.DetectionEngine;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.common.monitoring.Alarm;
import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdDetails;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdType;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.catalogue.MediaServer;
import org.openbaton.vnfm.repositories.MediaServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.lang.Iterable;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
public class DetectionTask implements Runnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private NFVORequestor nfvoRequestor;

    private Properties properties;

    private String nsr_id;

    private String vnfr_id;

    private AutoScalePolicy autoScalePolicy;

    private VirtualisedResourcesPerformanceManagement monitor;

    private String name;

    private boolean first_time;

    public void init(VirtualNetworkFunctionRecord vnfr, AutoScalePolicy autoScalePolicy, Properties properties) throws NotFoundException {
        this.properties = properties;
        this.nfvoRequestor = new NFVORequestor(this.properties.getProperty("openbaton-username"), this.properties.getProperty("openbaton-password"), this.properties.getProperty("openbaton-url"), this.properties.getProperty("openbaton-port"), "1");
        this.nsr_id = vnfr.getParent_ns_id();
        this.vnfr_id = vnfr.getId();
        this.autoScalePolicy = autoScalePolicy;
        this.name = "DetectionTask#" + vnfr.getId();
        log.debug("DetectionTask: Fetching the monitor");
        this.monitor = new DetectionEngine();
        if (monitor==null) {
            throw new NotFoundException("DetectionTask: Monitor was not found. Cannot start Autoscaling for VNFR with id: " + vnfr_id);
        }
        this.first_time = true;
    }

    @Override
    public void run() {
        if (first_time == true) {
            log.debug("Starting DetectionTask the first time. So wait for the cooldown...");
            first_time = false;
            try {
                Thread.sleep(autoScalePolicy.getCooldown() * 1000);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
        log.debug("DetectionTask: Checking AutoScalingPolicy " + autoScalePolicy.getName() + " with id: " + autoScalePolicy.getId() + " VNFR with id: " + vnfr_id);
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage());
        }
        if (vnfr != null) {
            for (ScalingAlarm alarm : autoScalePolicy.getAlarms()) {
                List<Item> measurementResults = null;
                try {
                    measurementResults = getRawMeasurementResults(vnfr, alarm.getMetric(), Integer.toString(autoScalePolicy.getPeriod()));
                } catch (MonitoringException e) {
                    e.printStackTrace();
                }
                double finalResult = calculateMeasurementResult(alarm, measurementResults);
                log.debug("DetectionTask: Final measurement result on vnfr " + vnfr.getId() + " on metric " + alarm.getMetric() + " with statistic " + alarm.getStatistic() + " is " + finalResult + " " + measurementResults);
                if (checkThreshold(alarm, finalResult)) {
                    //ToDo send event
                } else {
                    log.debug("DetectionTask: Scaling of AutoScalePolicy with id " + autoScalePolicy.getId() + " is not triggered");
                }
                log.debug("DetectionTask: Starting sleeping period (" + autoScalePolicy.getPeriod() + "s) for AutoScalePolicy with id: " + autoScalePolicy.getId());
            }
        } else {
            log.error("DetectionTask: Not found VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        }
    }

    public void waitForState(String nsrId, String vnfrId, Set<Status> states) {
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        VirtualNetworkFunctionRecord vnfr = getVnfr(nsrId, vnfrId);
        while (!states.contains(vnfr.getStatus())) {
            log.debug("DetectionTask: Waiting until status of VNFR with id: " + vnfrId + " goes back to " + states);
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
            vnfr = getVnfr(nsrId, vnfrId);
        }
    }

    public VirtualNetworkFunctionRecord getVnfr(String nsrId, String vnfrId) {
        try {
            return nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsrId, vnfrId);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public List<Item> getRawMeasurementResults(VirtualNetworkFunctionRecord vnfr, String metric, String period) throws MonitoringException {
        ArrayList<Item> measurementResults = new ArrayList<Item>();
        ArrayList<String> hostnames = new ArrayList<String>();
        ArrayList<String> metrics = new ArrayList<String>();
        metrics.add(metric);
        log.debug("Getting all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + ".");
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                hostnames.add(vnfcInstance.getHostname());
            }
        }
        log.debug("Getting all measurement results for hostnames " + hostnames + " on metric " + metric + ".");
        measurementResults.addAll(monitor.queryPMJob(hostnames, metrics, period));
        log.debug("Got all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + " -> " + measurementResults + ".");
        return measurementResults;
    }

    public double calculateMeasurementResult(ScalingAlarm alarm, List<Item> measurementResults) {
        double result;
        List<Double> consideredResults = new ArrayList<>();
        for (Item measurementResult : measurementResults) {
            consideredResults.add(Double.parseDouble(measurementResult.getValue()));
        }
        switch (alarm.getStatistic()) {
            case "avg":
                double sum = 0;
                for (Double consideredResult : consideredResults) {
                    sum += consideredResult;
                }
                result = sum / measurementResults.size();
                break;
            case "min":
                result = Collections.min(consideredResults);
                break;
            case "max":
                result = Collections.max(consideredResults);
                break;
            default:
                result = -1;
                break;
        }
        return result;
    }

    public boolean checkThreshold(ScalingAlarm alarm, double result) {
        switch (alarm.getComparisonOperator()) {
            case ">":
                if (result > alarm.getThreshold()) {
                    return true;
                }
                break;
            case "<":
                if (result < alarm.getThreshold()) {
                    return true;
                }
                break;
            case "=":
                if (result == alarm.getThreshold()) {
                    return true;
                }
                break;
            case "!=":
                if (result != alarm.getThreshold()) {
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }
}
