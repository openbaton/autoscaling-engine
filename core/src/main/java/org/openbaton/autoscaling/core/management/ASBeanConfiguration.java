/*
 *
 *  *
 *  *  * Copyright (c) 2016 Open Baton (http://www.openbaton.org)
 *  *  *
 *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  * you may not use this file except in compliance with the License.
 *  *  * You may obtain a copy of the License at
 *  *  *
 *  *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  * See the License for the specific language governing permissions and
 *  *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionEngine;
import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionEngine;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionEngine;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.sdk.NFVORequestor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Created by mpa on 02.02.16.
 */
@Configuration
@EnableAsync
public class ASBeanConfiguration {

  @Bean
  public ElasticityManagement elasticityManagement() {
    return new ElasticityManagement();
  }

  @Bean
  public DetectionManagement detectionManagement() {
    return new DetectionManagement();
  }

  @Bean
  public DetectionEngine detectionEngine() {
    return new DetectionEngine();
  }

  @Bean
  public DecisionManagement decisionManagement() {
    return new DecisionManagement();
  }

  @Bean
  public DecisionEngine decisionEngine() {
    return new DecisionEngine();
  }

  @Bean
  public ExecutionManagement executionManagement() {
    return new ExecutionManagement();
  }

  @Bean
  public ExecutionEngine executionEngine() {
    return new ExecutionEngine();
  }

}
