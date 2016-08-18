/*
 *
 *  *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.autoscaling.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;

@RestController
@RequestMapping("/executor")
public class RestExecutionInterface {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ExecutionManagement executionManagement;

    /**
     * Activates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void execute(@RequestBody String msg) throws NotFoundException {
        log.debug("========================");
        log.debug("msg=" + msg);
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
//        String actionsString = json.get("actions");
//        Set<Action> actions = mapper.fromJson(actionsString, Action.class);
//        log.debug("ACTION=" + action);
//        NetworkServiceRecord nsr = mapper.fromJson(json.get("payload"), NetworkServiceRecord.class);
//        log.debug("NSR=" + nsr);
//        detectionEngine.activate(nsr);
        String projectId = mapper.fromJson(json.get("project_id"), String.class);
        String nsrId = mapper.fromJson(json.get("nsr_id"), String.class);
        String vnfrId = mapper.fromJson(json.get("vnfr_id"), String.class);
        Long cooldown = mapper.fromJson(json.get("cooldown"), Long.class);
        executionManagement.executeActions(projectId, nsrId, new HashMap(), new HashSet<ScalingAction>(), cooldown);
    }

    /**
     * Deactivates autoscaling for the passed NSR
     *
     * @param msg : NSR in payload to add for autoscaling
     */
    @RequestMapping(value = "", method = RequestMethod.DELETE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void stop(@RequestBody String msg) throws NotFoundException {
        log.debug("========================");
        log.debug("msg=" + msg);
        JsonParser jsonParser = new JsonParser();
        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
        Gson mapper = new GsonBuilder().create();
//        Action action = mapper.fromJson(json.get("action"), Action.class);
//        log.debug("ACTION=" + action);
//        NetworkServiceRecord nsr = mapper.fromJson(json.get("payload"), NetworkServiceRecord.class);
//        log.debug("NSR=" + nsr);
        String projectId = mapper.fromJson(json.get("project_id"), String.class);
        String nsrId = mapper.fromJson(json.get("nsr_id"), String.class);
        String vnfrId = mapper.fromJson(json.get("vnfr_id"), String.class);
        executionManagement.stop(projectId, nsrId);
//        detectionEngine.deactivate(nsr);
    }

//    /**
//     * Stops autoscaling for the passed NSR
//     *
//     * @param msg : NSR in payload to add for autoscaling
//     */
//    @RequestMapping(value = "ERROR", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    @ResponseStatus(HttpStatus.CREATED)
//    public void stop(@RequestBody String msg) throws NotFoundException {
//        log.debug("========================");
//        log.debug("msg=" + msg);
//        JsonParser jsonParser = new JsonParser();
//        JsonObject json = jsonParser.parse(msg).getAsJsonObject();
//        Gson mapper = new GsonBuilder().create();
//        Action action = mapper.fromJson(json.get("action"), Action.class);
//        log.debug("ACTION=" + action);
////        try {
////            NetworkServiceRecord nsr = mapper.fromJson(json.get("payload"), NetworkServiceRecord.class);
////            log.debug("NSR=" + nsr);
////            elasticityManagement.deactivate(nsr);
////        } catch (NullPointerException e) {
////            VirtualNetworkFunctionRecord vnfr = mapper.fromJson(json.get("payload"), VirtualNetworkFunctionRecord.class);
////            log.debug("vnfr=" + vnfr);
////            elasticityManagement.deactivate(vnfr);
////        }
//    }

}
