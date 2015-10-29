package org.openbaton.autoscaling;

import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.plugin.utils.PluginStartup;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;

/**
 * Created by mpa on 27.10.15.
 */
@SpringBootApplication
@ComponentScan("org.openbaton.autoscaling")
public class Application {

    protected static Logger log = LoggerFactory.getLogger(Application.class);

    private Properties properties;

    private NFVORequestor nfvoRequestor;

    private String subscriptionId;

    @PostConstruct
    private void init() throws SDKException {
        properties = Utils.loadProperties();
        startPlugins();

        waitForNfvo();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        subscribe();
    }

    @PreDestroy
    private void exit() throws SDKException {
        unsubscribe();
        destroyPlugins();
    }

    private void subscribe() throws SDKException {
        log.debug("Subscribing to all Events with INSTANTIATE_FINISH");
        EventEndpoint eventEndpoint = new EventEndpoint();
        eventEndpoint.setName("AutoscalingEvent");
        eventEndpoint.setEndpoint("http://localhost:9999/event");
        eventEndpoint.setEvent(Action.INSTANTIATE_FINISH);
        eventEndpoint.setType(EndpointType.REST);
        this.subscriptionId = nfvoRequestor.getEventAgent().create(eventEndpoint).getId();
    }

    private void unsubscribe() throws SDKException {
        nfvoRequestor.getEventAgent().delete(subscriptionId);
    }

    private void startPlugins() {
        try {
            int registryport = 19999;
            Registry registry = LocateRegistry.createRegistry(registryport);
            log.debug("Registry created: ");
            log.debug(registry.toString() + " has: " + registry.list().length + " entries");
            PluginStartup.startPluginRecursive("./plugins", true, "localhost", "" + registryport);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void destroyPlugins() {
        PluginStartup.destroy();
    }

    public Properties getProperties() {
        return properties;
    }

    private void waitForNfvo() {
        if (!Utils.isNfvoStarted(properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"))) {
            log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
            System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
        }
    }
}
