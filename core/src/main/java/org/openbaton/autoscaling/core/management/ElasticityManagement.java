package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ElasticityManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DetectionManagement detectionManagment;

    @Autowired
    private DecisionManagement decisionManagement;

    @Autowired
    private ExecutionManagement executionManagement;

    @Autowired
    private PoolManagement poolManagement;

    private NFVORequestor nfvoRequestor;

    private Properties properties;

    private List<String> subscriptionIds;

    @PostConstruct
    public void init() throws SDKException {
        properties = Utils.loadProperties();
        log.debug("Properties: " + properties.toString());
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
//        detectionManagment.init(properties);
//        decisionManagement.init(properties);
//        executionManagement.init(properties);
//        if (properties.getProperty("pool_activated").equals(true)) {
//            poolManagement = new PoolManagement();
//            poolManagement.init(properties);
//        }

        subscriptionIds = new ArrayList<>();
        //startPlugins();

        waitForNfvo();
        //subscribe(Action.INSTANTIATE_FINISH);
        //subscribe(Action.RELEASE_RESOURCES_FINISH);
        //subscribe(Action.ERROR);

        //fetchNSRsFromNFVO();
    }

    @PreDestroy
    private void exit() throws SDKException {
        //unsubscribe();
        //destroyPlugins();
    }

    public void activate(String nsr_id) throws NotFoundException, VimException, VimDriverException {
        log.debug("Activating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.activate(nsr_id);
        if (properties.getProperty("pool_activated", "false").equals("true")) {
            log.debug("Activating pool mechanism");
            poolManagement.activate(nsr_id);
        } else {
            log.debug("pool mechanism is disabled");
        }
        log.info("Activated Elasticity for NSR with id: " + nsr_id);
    }

    public void deactivate(String nsr_id) throws NotFoundException {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.deactivate(nsr_id);
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
    }

    private void subscribe(Action action) throws SDKException {
        log.debug("Subscribing to all NSR Events with Action " + action);
        EventEndpoint eventEndpoint = new EventEndpoint();
        eventEndpoint.setName("Subscription:" + action);
        eventEndpoint.setEndpoint("http://localhost:9999/event/" + action);
        eventEndpoint.setEvent(action);
        eventEndpoint.setType(EndpointType.REST);
        this.subscriptionIds.add(nfvoRequestor.getEventAgent().create(eventEndpoint).getId());
    }

    private void unsubscribe() throws SDKException {
        for (String subscriptionId : subscriptionIds) {
            nfvoRequestor.getEventAgent().delete(subscriptionId);
        }
    }

    private void waitForNfvo() {
        if (!Utils.isNfvoStarted(properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"))) {
            log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
            System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
        }
    }

}
