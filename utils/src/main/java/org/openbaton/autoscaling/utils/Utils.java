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

package org.openbaton.autoscaling.utils;

import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by mpa on 28.10.15.
 */
public class Utils {

    protected static Logger log = LoggerFactory.getLogger(Utils.class);

    public static boolean isNfvoStarted(String ip, String port) {
        if (true)
            return true;
        int i = 0;
        log.info("Waiting until NFVO is available...");
        while (!Utils.available(ip, port)) {
            i++;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (i > 50) {
                return false;
            }

        }
        return true;
    }

    public static boolean available(String ip, String port) {
        try {
            Socket s = new Socket(ip, Integer.parseInt(port));
            log.info("NFVO is listening on port " + port + " at " + ip);
            s.close();
            return true;
        } catch (IOException ex) {
            // The remote host is not listening on this port
            log.warn("NFVO is not reachable on port " + port + " at " + ip);
            return false;
        }
    }

    public static Properties loadProperties() {
        Properties properties = new Properties();

        log.debug("Loading properties");
        try {
            //properties.load(Utils.class.getResourceAsStream("/openbaton.properties"));
            //properties.load(Utils.class.getResourceAsStream("/autoscaling.properties"));
            if (properties.getProperty("external-properties-file") != null) {
                File externalPropertiesFile = new File(properties.getProperty("external-properties-file"));
                if (externalPropertiesFile.exists()) {
                    log.debug("Loading properties from external-properties-file: " + properties.getProperty("external-properties-file"));
                    InputStream is = new FileInputStream(externalPropertiesFile);
                    properties.load(is);
                } else {
                    log.debug("external-properties-file: " + properties.getProperty("external-properties-file") + " doesn't exist");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("Loaded properties: " + properties);
        return properties;
    }

    public static void loadExternalProperties(ConfigurableEnvironment properties) {
        if (properties.containsProperty("external-properties-file") && properties.getProperty("external-properties-file") != null) {
            try {
                InputStream is = new FileInputStream(new File(properties.getProperty("external-properties-file")));
                Properties externalProperties = new Properties();
                externalProperties.load(is);
                PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("external-properties", externalProperties);

                MutablePropertySources propertySources = properties.getPropertySources();
                propertySources.addFirst(propertiesPropertySource);
            } catch (IOException e) {
                log.warn("Not found external-properties-file: " + properties.getProperty("external-properties-file"));
            }
        }
    }

    public static VimInstance getVimInstance(String name, List<VimInstance> vimInstances) throws NotFoundException {
        for (VimInstance vimInstance : vimInstances) {
            if (vimInstance.getName().equals(name)) {
                return vimInstance;
            }
        }
        throw new NotFoundException("VimInstance with name: " + name + " was not found in the provided list of VimInstances.");
    }

    public static VimInstance getVimInstance(String name, NFVORequestor nfvoRequestor) throws NotFoundException {
        List<VimInstance> vimInstances = new ArrayList<>();
        try {
            vimInstances = nfvoRequestor.getVimInstanceAgent().findAll();
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        for (VimInstance vimInstance : vimInstances) {
            if (vimInstance.getName().equals(name)) {
                return vimInstance;
            }
        }
        throw new NotFoundException("VimInstance with name: " + name + " was not found in the provided list of VimInstances.");
    }

}
