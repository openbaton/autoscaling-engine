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

import org.openbaton.catalogue.mano.common.Event;
import org.openbaton.catalogue.mano.common.LifecycleEvent;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by mpa on 28.10.15.
 */
public class Utils {

    private final static Logger log = LoggerFactory.getLogger(Utils.class);

    public static boolean isNfvoStarted(String ip, String port) {
        int i = 0;
        log.info("Waiting until NFVO is available...");
        while (!Utils.available(ip, port)) {
            i++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (i > 600) {
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

    public static String getUserdata() {
        StringBuilder sb = new StringBuilder();
        sb.append(getUserdataFromJar());
        sb.append(getUserdataFromFS());
        return sb.toString();
    }

    public static String getUserdataFromJar() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = {};
        StringBuilder script = new StringBuilder();
        try {
            resources = resolver.getResources("/scripts/*.sh");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        for (Resource resource : resources) {
            InputStream in = null;
            InputStreamReader is = null;
            BufferedReader br = null;
            try {
                in = resource.getInputStream();
                is = new InputStreamReader(in);
                br = new BufferedReader(is);
                String line = br.readLine();
                while (line != null) {
                    script.append(line).append("\n");
                    line = br.readLine();
                }
                script.append("\n");
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            } finally {
                try {
                    br.close();
                    is.close();
                    in.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return script.toString();
    }

    public static String getUserdataFromFS() {
        File folder = new File("/etc/openbaton/scripts/ms-vnfm");
        List<String> lines = new ArrayList<String>();
        if (folder.exists() && folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                if (file.getAbsolutePath().endsWith(".sh")) {
                    try {
                        lines.addAll(Files.readAllLines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                lines.add("\n");
            }
        }
        //Create the script
        StringBuilder script = new StringBuilder();
        for (String line : lines) {
            script.append(line).append("\n");
        }
        return script.toString();
    }

    public static void loadExternalProperties(Properties properties) {
        if (properties.getProperty("external-properties-file") != null) {
            File externalPropertiesFile = new File(properties.getProperty("external-properties-file"));
            if (externalPropertiesFile.exists()) {
                log.debug("Loading properties from external-properties-file: " + properties.getProperty("external-properties-file"));
                InputStream is = null;
                try {
                    is = new FileInputStream(externalPropertiesFile);
                    properties.load(is);
                } catch (FileNotFoundException e) {
                    log.error(e.getMessage(), e);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                log.debug("external-properties-file: " + properties.getProperty("external-properties-file") + " doesn't exist");
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

    public static VimInstance getVimInstance(List<String> vimInstanceNames, NFVORequestor nfvoRequestor) throws NotFoundException {
        List<VimInstance> vimInstances = new ArrayList<>();
        try {
            vimInstances = nfvoRequestor.getVimInstanceAgent().findAll();
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        for (String vimInstanceName : vimInstanceNames) {
            for (VimInstance vimInstance : vimInstances) {
                if (vimInstance.getName().equals(vimInstanceName)) {
                    return vimInstance;
                }
            }
        }
        throw new NotFoundException("VimInstances with names: " + vimInstanceNames + " were not found in the provided list of VimInstances.");
    }

    public Set<Event> listEvents(VirtualNetworkFunctionRecord vnfr) {
        Set<Event> events = new HashSet<Event>();
        for (LifecycleEvent event : vnfr.getLifecycle_event()) {
            events.add(event.getEvent());
        }
        return events;
    }

    public void removeEvent(VirtualNetworkFunctionRecord vnfr, Event event) throws NotFoundException {
        LifecycleEvent lifecycleEvent = null;
        if (vnfr.getLifecycle_event_history() == null)
            vnfr.setLifecycle_event_history(new HashSet<LifecycleEvent>());
        for (LifecycleEvent tmpLifecycleEvent : vnfr.getLifecycle_event()) {
            if (event.equals(tmpLifecycleEvent.getEvent())) {
                lifecycleEvent = tmpLifecycleEvent;
                vnfr.getLifecycle_event_history().add(lifecycleEvent);
                break;
            }
        }
        if (lifecycleEvent == null) {
            throw new NotFoundException("Not found LifecycleEvent with event " + event);
        }
    }

    public Set<Event> listHistoryEvents(VirtualNetworkFunctionRecord vnfr) {
        Set<Event> events = new HashSet<Event>();
        if (vnfr.getLifecycle_event_history() != null) {
            for (LifecycleEvent event : vnfr.getLifecycle_event_history()) {
                events.add(event.getEvent());
            }
        }
        return events;
    }

    public static String replaceVariables(String userdataRaw, Map<String, String> variables) {
        String userdata = userdataRaw;
        for (String variable : variables.keySet()) {
            //if (!variables.get(variable).equals("")) {
            log.debug("Replace " + variable + " with value " + variables.get(variable));
            userdata = userdata.replaceAll(Pattern.quote(variable), variables.get(variable));
            log.debug("Replaced userdata: " + userdata);
            //} else {
            //    log.warn("Variable " + variable + " is not defined. So not replace it");
            //}
        }
        return userdata;
    }
}
