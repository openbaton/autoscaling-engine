package org.openbaton.autoscaling;

import org.openbaton.autoscaling.configuration.PropertiesConfiguration;
import org.openbaton.autoscaling.core.management.ASBeanConfiguration;
import org.openbaton.autoscaling.core.management.ElasticityManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.plugin.utils.PluginStartup;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.autoscaling.configuration.AutoScalingProperties;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.configuration.SpringProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mpa on 27.10.15.
 */
@SpringBootApplication
//@EntityScan("org.openbaton.autoscaling.catalogue")
@ComponentScan({"org.openbaton.autoscaling.api", "org.openbaton.autoscaling", "org.openbaton"})
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {ASBeanConfiguration.class , PropertiesConfiguration.class})
public class Application {

    protected static Logger log = LoggerFactory.getLogger(Application.class);

    @Autowired
    private ConfigurableApplicationContext context;

    private NFVORequestor nfvoRequestor;

    private List<String> subscriptionIds;

    @Autowired
    private AutoScalingProperties autoScalingProperties;

    @Autowired
    private SpringProperties springProperties;

    @Autowired
    private NfvoProperties nfvoProperties;

    private ElasticityManagement elasticityManagement;

    @PostConstruct
    private void init() throws SDKException {
        //start all the plugins needed
        startPlugins();
        //waiting until the NFVO is available
        waitForNfvo();
        this.elasticityManagement = context.getBean(ElasticityManagement.class);
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
        subscriptionIds = new ArrayList<>();
        subscriptionIds.add(subscribe(Action.INSTANTIATE_FINISH));
        subscriptionIds.add(subscribe(Action.RELEASE_RESOURCES_FINISH));
        subscriptionIds.add(subscribe(Action.ERROR));

        fetchNSRsFromNFVO();
    }

    @PreDestroy
    private void exit() throws SDKException {
        unsubscribe();
        destroyPlugins();
    }

    private String subscribe(Action action) throws SDKException {
        log.debug("Subscribing to all NSR Events with Action " + action);
        EventEndpoint eventEndpoint = new EventEndpoint();
        eventEndpoint.setName("Subscription:" + action);
        eventEndpoint.setEndpoint("http://" + autoScalingProperties.getServer().getIp() + ":" + autoScalingProperties.getServer().getPort() + "/elasticity-management/" + action);
        eventEndpoint.setEvent(action);
        eventEndpoint.setType(EndpointType.REST);
        return nfvoRequestor.getEventAgent().create(eventEndpoint).getId();
    }

    private void unsubscribe() throws SDKException {
        for (String subscriptionId : subscriptionIds) {
            nfvoRequestor.getEventAgent().delete(subscriptionId);
        }
    }

    private void startPlugins() {
        try {
            PluginStartup.startPluginRecursive("./plugins", true, autoScalingProperties.getRabbitmq().getBrokerIp(), String.valueOf(springProperties.getRabbitmq().getPort()), 15, springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), autoScalingProperties.getRabbitmq().getManagement().getPort());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void destroyPlugins() {
        PluginStartup.destroy();
    }

    private void waitForNfvo() {
        if (!Utils.isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
            log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
            System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
        }
    }

    private void fetchNSRsFromNFVO() {
        log.debug("Fetching previously deployed NSRs from NFVO to start the autoscaling for them.");
        List<NetworkServiceRecord> nsrs = null;
        try {
            nsrs = nfvoRequestor.getNetworkServiceRecordAgent().findAll();
        } catch (SDKException e) {
            log.warn("Problem while fetching exisiting NSRs from the Orchestrator to start Autoscaling. Elasticity for previously deployed NSRs will not start", e);
            return;
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
            return;
        }
        for (NetworkServiceRecord nsr : nsrs) {
            try {
                if (nsr.getStatus() == Status.ACTIVE || nsr.getStatus() == Status.SCALING) {
                    log.debug("Adding previously deployed NSR with id: " + nsr.getId() + " to autoscaling");
                    try {
                        elasticityManagement.activate(nsr);
                    } catch (VimException e) {
                        log.error(e.getMessage(), e);
                    }
                } else {
                    log.warn("Cannot add NSR with id: " + nsr.getId() + " to autoscaling because it is in state: " + nsr.getStatus() + " and not in state " + Status.ACTIVE + " or " + Status.ERROR + ". ");
                }
            } catch (NotFoundException e) {
                log.warn("Not found NSR with id: " + nsr.getId());
            }
        }
    }

}
