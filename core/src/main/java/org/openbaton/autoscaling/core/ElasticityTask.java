package org.openbaton.autoscaling.core;

import org.openbaton.autoscaling.catalogue.VnfrMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.catalogue.nfvo.messages.Interfaces.NFVMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
class ElasticityTask implements Runnable {

    protected static final String nfvoQueue = "vnfm-core-actions";

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ElasticityManagement elasticityManagement;

//    @Autowired
//    protected JmsTemplate jmsTemplate;

    @Autowired
    private VnfrMonitor monitor;

    private String vnfr_id;

    private AutoScalePolicy autoScalePolicy;

    private String name;

    public void init(VirtualNetworkFunctionRecord vnfr, AutoScalePolicy autoScalePolicy) {
        this.monitor.addVNFR(vnfr);
        this.vnfr_id = vnfr.getId();
        this.autoScalePolicy = autoScalePolicy;
        this.name = "ElasticityTask#" + vnfr.getId();
    }

    @Override
    public void run() {
        log.debug("Check if scaling is needed.");
        try {
            VirtualNetworkFunctionRecord vnfr = monitor.getVNFR(vnfr_id);
            List<Item> measurementResults = elasticityManagement.getRawMeasurementResults(vnfr, autoScalePolicy.getMetric(), Integer.toString(autoScalePolicy.getPeriod()));
            double finalResult = elasticityManagement.calculateMeasurementResult(autoScalePolicy, measurementResults);
            log.debug("Final measurement result on vnfr " + vnfr.getId() + " on metric " + autoScalePolicy.getMetric() + " with statistic " + autoScalePolicy.getStatistic() + " is " + finalResult + " " + measurementResults);
            if (vnfr.getStatus().equals(Status.ACTIVE)) {
                if (elasticityManagement.triggerAction(autoScalePolicy, finalResult) && elasticityManagement.checkFeasibility(vnfr, autoScalePolicy) && setStatus(Status.SCALING) == true) {
                    log.debug("Executing scaling action of AutoScalePolicy with id " + autoScalePolicy.getId());
                    elasticityManagement.scaleVNFComponents(vnfr, autoScalePolicy);
//                    vnfr = updateOnNFVO(vnfr, Action.SCALING);
                    elasticityManagement.scaleVNFCInstances(vnfr);
                    log.debug("Starting cooldown period (" + autoScalePolicy.getCooldown() + "s) for AutoScalePolicy with id: " + autoScalePolicy.getId());
                    Thread.sleep(autoScalePolicy.getCooldown() * 1000);
                    log.debug("Finished cooldown period (" + autoScalePolicy.getCooldown() + "s) for AutoScalePolicy with id: " + autoScalePolicy.getId());
//                    vnfr = updateOnNFVO(vnfr, Action.SCALED);
                } else {
                    log.debug("Scaling of AutoScalePolicy with id " + autoScalePolicy.getId() + " is not executed");
                }
            } else {
                log.debug("ElasticityTask: In State Scaling -> waiting until finished");
            }
            log.debug("Starting sleeping period (" + autoScalePolicy.getPeriod() + "s) for AutoScalePolicy with id: " + autoScalePolicy.getId());
        } catch (InterruptedException e) {
            log.warn("ElasticityTask was interrupted");
        } catch (RemoteException e) {
            log.warn("Problem with the MonitoringPlugin!");
        }
    }

    private synchronized boolean setStatus(Status status) {
        log.debug("Set status of vnfr " + monitor.getVNFR(vnfr_id).getId() + " to " + status.name());
        Collection<Status> nonBlockingStatus = new HashSet<Status>();
        nonBlockingStatus.add(Status.ACTIVE);
        if (nonBlockingStatus.contains(this.monitor.getVNFR(vnfr_id).getStatus()) || status.equals(Status.ACTIVE)) {
            monitor.getVNFR(vnfr_id).setStatus(status);
            return true;
        } else {
            return false;
        }
    }

//    protected VirtualNetworkFunctionRecord updateOnNFVO(VirtualNetworkFunctionRecord vnfr, Action action) {
//        NFVMessage response = null;
//        try {
//            response = vnfmHelper.sendAndReceive(VnfmUtils.getNfvMessage(action, vnfr));
//        } catch (JMSException e) {
//            log.error("" + e.getMessage());
//            vnfr.setStatus(Status.ERROR);
//            return vnfr;
//        } catch (Exception e) {
//            vnfr.setStatus(Status.ERROR);
//            log.error("" + e.getMessage());
//            return vnfr;
//        }
//        log.debug("" + response);
//        if (response.getAction().ordinal() == Action.ERROR.ordinal()) {
//            vnfr.setStatus(Status.ERROR);
//            return vnfr;
//        }
//        OrVnfmGenericMessage orVnfmGenericMessage = (OrVnfmGenericMessage) response;
//        vnfr = orVnfmGenericMessage.getVnfr();
//        this.monitor.addVNFR(vnfr);
//        return vnfr;
//    }

}
