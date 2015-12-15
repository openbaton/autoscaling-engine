/*
 * Copyright (c) 2015 Fraunhofer FOKUS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openbaton.autoscaling.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.openbaton.autoscaling.core.detection.DetectionEngine;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdDetails;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdType;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.List;

@RestController
@RequestMapping("/vrpm")
public class RestDetectionInterface {

	private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DetectionManagement detectionManagement;

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "createPMJob", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    //public String createPMJob(ObjectSelection resourceSelector, List<String> performanceMetric, List<String> performanceMetricGroup, Integer collectionPeriod, Integer reportingPeriod) throws MonitoringException {
    public String createPMJob(@RequestBody String msg) throws MonitoringException {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
        //Parse JSon
        ObjectSelection resourceSelection = mapper.fromJson(json.get("resourceSelector"), ObjectSelection.class);
        Type typeOfList = new TypeToken<List<String>>(){}.getType();
        List<String> performanceMetric = mapper.fromJson(json.get("performanceMetric"), typeOfList);
        List<String> performanceMetricGroup = mapper.fromJson(json.get("performanceMetricGroup"), typeOfList);
        Integer collectionPeriod = mapper.fromJson(json.get("collectionPeriod"), Integer.class);
        Integer reportingPeriod = mapper.fromJson(json.get("reportingPeriod"), Integer.class);

        return detectionManagement.createPMJob(resourceSelection, performanceMetric, performanceMetricGroup, collectionPeriod, reportingPeriod);
    }

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "deletePMJob", method = RequestMethod.DELETE, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    //public List<String> deletePMJob(List<String> itemIdsToDelete) throws MonitoringException {
    public List<String> deletePMJob(@RequestBody String msg) throws MonitoringException {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
        //Parse JSon
        Type typeOfList = new TypeToken<List<String>>(){}.getType();
        List<String> itemIdsToDelete = mapper.fromJson(json.get("itemIdsToDelete"), typeOfList);

        return detectionManagement.deletePMJob(itemIdsToDelete);
    }

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "queryPMJob", method = RequestMethod.GET, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    //public List<Item> queryPMJob(List<String> hostnames, List<String> metrics, String period) throws MonitoringException {
    public List<Item> queryPMJob(@RequestBody String msg) throws MonitoringException {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
        //Parse JSon
        Type typeOfList = new TypeToken<List<String>>(){}.getType();
        List<String> hostnames = mapper.fromJson(json.get("hostnames"), typeOfList);
        List<String> metrics = mapper.fromJson(json.get("metrics"), typeOfList);
        String period = mapper.fromJson(json.get("period"), String.class);
        return detectionManagement.queryPMJob(hostnames, metrics, period);
    }

    public void subscribe() {
    }

    public void notifyInfo() {

    }

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "createThreshold", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    //public String createThreshold(ObjectSelection objectSelector, String performanceMetric, ThresholdType thresholdType, ThresholdDetails thresholdDetails) throws MonitoringException {
    public String createThreshold(@RequestBody String msg) throws MonitoringException {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
        //Parse JSon
        ObjectSelection resourceSelection = mapper.fromJson(json.get("resourceSelector"), ObjectSelection.class);
        String performanceMetric = mapper.fromJson(json.get("performanceMetric"), String.class);
        ThresholdType thresholdType = mapper.fromJson(json.get("thresholdType"), ThresholdType.class);
        ThresholdDetails thresholdDetails = mapper.fromJson(json.get("thresholdDetails"), ThresholdDetails.class);

        return detectionManagement.createThreshold(resourceSelection, performanceMetric, thresholdType, thresholdDetails);
    }

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "deleteThreshold", method = RequestMethod.DELETE, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    //public List<String> deleteThreshold(List<String> thresholdIds) throws MonitoringException {
    public List<String> deleteThreshold(@RequestBody String msg) throws MonitoringException {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
        //Parse JSon
        Type typeOfList = new TypeToken<List<String>>(){}.getType();
        List<String> thresholdIds = mapper.fromJson(json.get("thresholdIds"), typeOfList);
        return detectionManagement.deleteThreshold(thresholdIds);
    }

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "queryThreshold", method = RequestMethod.DELETE, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    //public void queryThreshold(String queryFilter) {
    public void queryThreshold(@RequestBody String msg) {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
        //Parse JSon
        String queryFilter = mapper.fromJson(json.get("queryFilter"), String.class);
        detectionManagement.queryThreshold(queryFilter);
        //ToDo add return value
    }
}
