package org.openbaton.autoscaling.core.detection.task;

import org.openbaton.autoscaling.catalogue.ScalingStatus;
import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionEngine;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.utils.Utils;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Iterable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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

    private DetectionEngine detectionEngine;

    private Properties properties;

    private String nsr_id;

    private String vnfr_id;

    private AutoScalePolicy autoScalePolicy;

    private String name;

    private boolean first_time;

    private boolean fired;

    public DetectionTask(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy, Properties properties, DetectionEngine detectionEngine) throws NotFoundException {
        this.nsr_id = nsr_id;
        this.vnfr_id = vnfr_id;
        this.autoScalePolicy = autoScalePolicy;
        this.properties = properties;
        this.detectionEngine = detectionEngine;

        this.nfvoRequestor = new NFVORequestor(this.properties.getProperty("nfvo.username"), this.properties.getProperty("nfvo.password"), this.properties.getProperty("nfvo.ip"), this.properties.getProperty("nfvo.port"), "1");
        this.name = "DetectionTask#" + nsr_id + ":" + vnfr_id;
        this.first_time = true;
        this.fired = false;
    }

    @Override
    public void run() {
        double alarmsWeightFired = 0;
        double alarmsWeightCount = 0;
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
                alarmsWeightCount =+ alarm.getWeight();
                List<Item> measurementResults = null;
                try {
                    measurementResults = detectionEngine.getRawMeasurementResults(vnfr, alarm.getMetric(), Integer.toString(autoScalePolicy.getPeriod()));
                } catch (MonitoringException e) {
                    log.error(e.getMessage(), e);
                }
                double finalAlarmResult = detectionEngine.calculateMeasurementResult(alarm, measurementResults);
                log.debug("DetectionTask: Measurement result on vnfr " + vnfr.getId() + " on metric " + alarm.getMetric() + " with statistic " + alarm.getStatistic() + " is " + finalAlarmResult + " " + measurementResults);
                if (detectionEngine.checkThreshold(alarm.getComparisonOperator(), alarm.getThreshold(), finalAlarmResult)) {
                    alarmsWeightFired =+ alarm.getWeight();
                    log.info("DetectionTask: Alarm with id: " + alarm.getId() + " of AutoScalePolicy with id " + autoScalePolicy.getId() + " is fired");
                } else {
                    log.debug("DetectionTask: Alarm with id: " + alarm.getId() + " of AutoScalePolicy with id " + autoScalePolicy.getId() + " is not fired");
                }
            }
            log.debug("Finished check of all Alarms of AutoScalePolicy with id " + autoScalePolicy.getId());
            //Check if Alarm must be fired for this AutoScalingPolicy
            double finalResult = (100 * alarmsWeightFired) / alarmsWeightCount;
            log.debug("Checking if AutoScalingPolicy with id " + autoScalePolicy.getId() + " must be executed");
            if (detectionEngine.checkThreshold(autoScalePolicy.getComparisonOperator(), autoScalePolicy.getThreshold(), finalResult)) {
                //if (fired == false) {
                    log.info("Threshold of AutoScalingPolicy with id " + autoScalePolicy.getId() + " is crossed -> " + autoScalePolicy.getThreshold() + autoScalePolicy.getComparisonOperator() + finalResult);
                    fired = true;
                    detectionEngine.sendAlarm(nsr_id, vnfr_id, autoScalePolicy);
                //} else {
                //    log.debug("Threshold of AutoScalingPolicy with id " + autoScalePolicy.getId() + " was already crossed. So don't FIRE it again and wait for CLEARED-> " + autoScalePolicy.getThreshold() + autoScalePolicy.getComparisonOperator() + finalResult);
                //}
            } else {
                if (fired == false) {
                    log.debug("Threshold of AutoScalingPolicy with id " + autoScalePolicy.getId() + " is not crossed -> " + finalResult + autoScalePolicy.getComparisonOperator() + autoScalePolicy.getThreshold());
                } else {
                    log.info("Threshold of AutoScalingPolicy with id " + autoScalePolicy.getId() + " is not crossed anymore. This means that the Alarm is cleared -> " + autoScalePolicy.getThreshold() + autoScalePolicy.getComparisonOperator() + finalResult);
                    fired = false;
                    //ToDo throw event CLEARED
                }
            }
        } else {
            log.error("DetectionTask: Not found VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        }
        log.debug("DetectionTask: Starting sleeping period (" + autoScalePolicy.getPeriod() + "s) for AutoScalePolicy with id: " + autoScalePolicy.getId());
    }
}



