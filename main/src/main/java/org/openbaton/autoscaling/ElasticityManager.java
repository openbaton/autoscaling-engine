package org.openbaton.autoscaling;

import org.openbaton.autoscaling.core.ElasticityManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.Event;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by mpa on 27.10.15.
 */
@SpringBootApplication
@ComponentScan("org.openbaton.autoscaling.api")
public class ElasticityManager {

    protected static Logger log = LoggerFactory.getLogger(ElasticityManager.class);

    private Properties properties;

    private NFVORequestor nfvoRequestor;

    @PostConstruct
    private void init() throws SDKException {
        properties = Utils.loadProperties();
        if (!Utils.isNfvoStarted(properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"))) {
            log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
            System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
        }
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        this.subscribe();
    }

    public void subscribe() throws SDKException {
        log.debug("Subscribing to all Events with INSTANTIATE_FINISH");
        EventEndpoint eventEndpoint = new EventEndpoint();
        eventEndpoint.setName("AutoscalingEvent");
        eventEndpoint.setEndpoint("http://localhost:9999/event");
        eventEndpoint.setEvent(Action.INSTANTIATE_FINISH);
        eventEndpoint.setType(EndpointType.REST);
        nfvoRequestor.getEventAgent().create(eventEndpoint);
    }

    public void unsubscribe(String eventId) throws SDKException {
        nfvoRequestor.getEventAgent().delete(eventId);
    }

    public Properties getProperties() {
        return properties;
    }
}
