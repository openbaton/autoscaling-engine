/*
 *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.openbaton.autoscaling.core.detection;

import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.monitoring.interfaces.MonitoringPlugin;
import org.openbaton.monitoring.interfaces.MonitoringPluginCaller;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.autoscaling.configuration.AutoScalingProperties;
import org.openbaton.autoscaling.configuration.SpringProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DetectionEngine {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ConfigurableApplicationContext context;

    private MonitoringPlugin monitor;

    //@Autowired
    private DetectionManagement detectionManagement;

    @Autowired
    private AutoScalingProperties autoScalingProperties;

    @Autowired
    private SpringProperties springProperties;

    @PostConstruct
    public void init() {
        this.detectionManagement = context.getBean(DetectionManagement.class);
    }

    public void initializeMonitor() {
        this.monitor = (MonitoringPluginCaller) ((RabbitPluginBroker) context.getBean(RabbitPluginBroker.class)).getMonitoringPluginCaller(autoScalingProperties.getRabbitmq().getBrokerIp(), springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), springProperties.getRabbitmq().getPort(),"zabbix-plugin", "zabbix", autoScalingProperties.getRabbitmq().getManagement().getPort());
        if (monitor == null) {
            log.warn("DetectionTask: Monitor was not found. Cannot start Autoscaling...");
        }
    }
//    public void waitForState(String nsrId, String vnfrId, Set<Status> states) {
//        try {
//            Thread.sleep(15000);
//        } catch (InterruptedException e) {
//            log.error(e.getMessage(), e);
//        }
//        VirtualNetworkFunctionRecord vnfr = getVnfr(nsrId, vnfrId);
//        while (!states.contains(vnfr.getStatus())) {
//            log.debug("DetectionTask: Waiting until status of VNFR with id: " + vnfrId + " goes back to " + states);
//            try {
//                Thread.sleep(10000);
//            } catch (InterruptedException e) {
//                log.error(e.getMessage(), e);
//            }
//            vnfr = getVnfr(nsrId, vnfrId);
//        }
//    }
//
//    public VirtualNetworkFunctionRecord getVnfr(String nsrId, String vnfrId) {
//        try {
//            return nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsrId, vnfrId);
//        } catch (SDKException e) {
//            log.error(e.getMessage(), e);
//        }
//        return null;
//    }

    public List<Item> getRawMeasurementResults(VirtualNetworkFunctionRecord vnfr, String metric, String period) throws MonitoringException {
        if (monitor == null) {
            initializeMonitor();
        }
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
        log.trace("Getting all measurement results for hostnames " + hostnames + " on metric " + metric + ".");
        measurementResults.addAll(monitor.queryPMJob(hostnames, metrics, period));
        log.debug("Got all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + " -> " + measurementResults + ".");
        return measurementResults;
    }

    public double calculateMeasurementResult(ScalingAlarm alarm, List<Item> measurementResults) {
        log.debug("Calculating final measurement result...");
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

    public boolean checkThreshold(String comparisonOperator, double threshold, double result) {
        log.debug("Checking Threshold...");
        switch (comparisonOperator) {
            case ">":
                if (result > threshold) {
                    return true;
                }
                break;
            case ">=":
                if (result >= threshold) {
                    return true;
                }
                break;
            case "<":
                if (result < threshold) {
                    return true;
                }
                break;
            case "<=":
                if (result <= threshold) {
                    return true;
                }
                break;
            case "=":
                if (result == threshold) {
                    return true;
                }
                break;
            case "!=":
                if (result != threshold) {
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public void sendAlarm(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
        detectionManagement.sendAlarm(nsr_id, vnfr_id, autoScalePolicy);
    }
}
