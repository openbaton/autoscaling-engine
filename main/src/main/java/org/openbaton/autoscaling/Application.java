package org.openbaton.autoscaling;

import org.openbaton.autoscaling.core.ElasticityManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.ConfigurationParameter;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.plugin.utils.PluginStartup;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by mpa on 27.10.15.
 */
@SpringBootApplication
@ComponentScan("org.openbaton.autoscaling")
//@PropertySource({
//        "classpath:autoscaling.properties",
//        "classpath:openbaton.properties"
//})
public class Application {

    protected static Logger log = LoggerFactory.getLogger(Application.class);

    private NFVORequestor nfvoRequestor;

    private List<String> subscriptionIds;

//    @Autowired
//    private ConfigurableEnvironment properties;

    @Autowired
    private ElasticityManagement elasticityManagement;

    private Properties properties;

    @PostConstruct
    private void init() throws SDKException {
        properties = Utils.loadProperties();
        //Utils.loadExternalProperties(properties);

        elasticityManagement.init(properties);

        subscriptionIds = new ArrayList<>();
        startPlugins();

        waitForNfvo();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        subscribe(Action.INSTANTIATE_FINISH);
        subscribe(Action.RELEASE_RESOURCES_FINISH);
        subscribe(Action.ERROR);
    }

    @PreDestroy
    private void exit() throws SDKException {
        unsubscribe();
        destroyPlugins();
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

    private void waitForNfvo() {
        if (!Utils.isNfvoStarted(properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"))) {
            log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
            System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
        }
    }
}
