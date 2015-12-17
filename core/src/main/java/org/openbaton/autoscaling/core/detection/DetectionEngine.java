package org.openbaton.autoscaling.core.detection;

import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdDetails;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdType;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DetectionEngine implements VirtualisedResourcesPerformanceManagement{

    public DetectionEngine() {

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
        return null;
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
