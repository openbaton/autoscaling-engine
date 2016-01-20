package org.openbaton.autoscaling.core.detection;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdDetails;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdType;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DetectionEngine {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private VirtualisedResourcesPerformanceManagement monitor;

    @Autowired
    private DecisionManagement decisionManagement;

    @PostConstruct
    public void init() {
        this.monitor = new EmmMonitor();
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

    public boolean checkThreshold(String comparisonOperator, double threshold, double result) {
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
        decisionManagement.decide(nsr_id, vnfr_id, autoScalePolicy);
    }
}

class EmmMonitor implements VirtualisedResourcesPerformanceManagement{

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private Properties properties;

    private String monitoringURL;


    public EmmMonitor() {
        properties = Utils.loadProperties();
        monitoringURL = properties.getProperty("emm.monitor.url");

    }

    @Override
    public String createPMJob(ObjectSelection resourceSelector, List<String> performanceMetric, List<String> performanceMetricGroup, Integer collectionPeriod, Integer reportingPeriod) throws MonitoringException {
        return null;
    }

    @Override
    public List<String> deletePMJob(List<String> itemIdsToDelete) throws MonitoringException {
        return null;
    }

    @Override
    public List<Item> queryPMJob(List<String> hostnames, List<String> metrics, String period) throws MonitoringException {
        log.debug("Requesting measurement results for hosts: " + hostnames + " on metrics: " + metrics + " (period: " + period + ")");
        List<Item> items = new ArrayList<>();
        for (String metric : metrics) {
            for (String hostName : hostnames) {
                try {
                    URL url = new URL("http://" + monitoringURL + "/monitor/" + hostName + "/" + metric);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json");
                    if (conn.getResponseCode() != 200) {
                        throw new RuntimeException("Failed : HTTP error code : "
                                + conn.getResponseCode());
                    }
                    BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
                    String output;
                    while ((output = br.readLine()) != null) {
                        log.debug("Measurement result for host " + hostName + " on metric " + metric + " is " + output);
                        Item item = new Item();
                        item.setHostname(hostName);
                        item.setHostId(hostName);
                        item.setLastValue(output);
                        item.setValue(output);
                        item.setMetric(metric);
                        items.add(item);
                    }
                    conn.disconnect();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return items;
    }

    @Override
    public void subscribe() {

    }

    @Override
    public void notifyInfo() {

    }

    @Override
    public String createThreshold(ObjectSelection objectSelector, String performanceMetric, ThresholdType thresholdType, ThresholdDetails thresholdDetails) throws MonitoringException {
        return null;
    }

    @Override
    public List<String> deleteThreshold(List<String> thresholdIds) throws MonitoringException {
        return null;
    }

    @Override
    public void queryThreshold(String queryFilter) {

    }

}
