  <img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/openBaton.png" width="250"/>
  
  Copyright © 2015-2016 [Open Baton](http://openbaton.org). 
  Licensed under [Apache v2 License](http://www.apache.org/licenses/LICENSE-2.0).

# AutoScaling System

OpenBaton is an open source project providing a reference implementation of the NFVO and VNFM based on the ETSI [NFV MANO] specification.

This project provides the first version of an NFV-compliant AutoScaling System. In the following fundamentals are described such as installing the AutoScaling System, configuring it and how to create AutoScaling policies.

The `autoscaling-engine` is implemented in java using the [spring.io] framework. It runs as an external component and communicates with the NFVO via Open Baton's SDK.

Additionally, the AutoScaling System uses the plugin mechanism to allow whatever Monitoring System you prefer. We use [Zabbix][zabbix] as the monitoring system in the following that must be preinstalled and configured. Additional information about [zabbix-plugin] can be found [here][zabbix-plugin-doc].

# How to install AutoScaling System

## Install the latest AutoScaling System version from the source code

The latest stable version AutoScaling System can be cloned from this [repository][autoscaling-repo] by executing the following command:

```bash
git clone https://github.com/openbaton/autoscaling.git
```

Once this is done, go inside the cloned folder and make use of the provided script to compile the project as done below:

```bash
./autoscaling-engine.sh compile
```

Before starting it you have to do the configuration of the AutoScaling System that is described in the [next chapter](#configuring-the-autoscaling-system) followed by the guide of [how to start](#starting-the-autoscaling-system) and [how to use](#how-to-use-the-autoscaling-system) it.

## Configuring the AutoScaling System

This chapter describes what needs to be done before starting the AutoScaling System. This includes the configuration file and properties, and also how to make use of monitoring plugin.

### Configuration file
The configuration file must be copied to `etc/openbaton/autoscaling.properties` by executing the following command from inside the repository folder:

```bash
cp etc/autoscaling.properties /etc/openbaton/autoscaling.properties
```

If done, check out the following chapter in order to understand the configuration parameters.

### Configuration properties

This chapter describes the parameters that must be considered for configuring the AutoScaling System.

| Params          				| Meaning       																|
| -------------   				| -------------																|
| logging.file					| location of the logging file |
| logging.level.*               | logging levels of the defined modules  |
| autoscaling.server.ip         | IP where the AutoScaling System is running. localhost might fit for most in the case when the System is running locally. If the System is running on another machine than the NFVO, you have to set the external IP here in order to subscribe for events towards the NFVO properly.      	|
| autoscaling.server.port       | Port where the System is reachable |
| autoscaling.rabbitmq.brokerIp | IP of the machine where RabbitMQ is running. This is needed for communicating with the monitoring plugin.	|
| spring.rabbitmq.username      | username for authorizing towards RabbitMQ |
| spring.rabbitmq.password      | password for authorizing towards RabbitMQ |
| nfvo.ip                       | IP of the NFVO |
| nfvo.port                     | Port of the NFVO |
| nfvo.username                 | username for authorizing towards NFVO |
| nfvo.password                 | password for authorizing towards NFVO |

### Monitoring plugin

The montoring plugin must be placed in the folder `plugins`. The zabbix plugin can be found [here][zabbix-plugin] with additional information about how to use and how to compile it.
If the plugin is placed in the folder mentioned before, it will be started automatically when starting the AutoScaling System.

**Note** If the NFVO is already in charge of starting the plugin, you should avoid to start it a second time from the AutoScaling System. Once started it can be used by all components.

## Starting the AutoScaling System

Starting the AutoScaling System can be achieved easily by using the the provided script with the following command:

```bash
./autoscaling-engine.sh start
```

Once the AutoScaling System is started, you can access the screen session by executing:

```bash
screen -r autoscaling-engine
```

**Note** Since the AutoScaling System subscribes to specific events towards the NFVO, you should take care about that the NFVO is already running when starting the AutoScaling System. Otherwise the AutoScaling System will wait for 600 seconds for the availability of the NFVO before terminating automatically.

# How to use the AutoScaling System

This guide shows you how to make use of the AutoScaling System. In particular, it describes how to define AutoScaling Policies.

### Creating AutoScaling Policies

A AutoScaling Policy defines conditions and actions in order to allow automatic scaling at runtime. The list of AutoScalePolicies are defined at the level of the VNFD/VNFR.
An example of an AutoScalePolicy can be found below followed by descriptions for each parameter.

```json
"auto_scale_policy":[
  {
    "name":"scale-out",
    "threshold":100,
    "comparisonOperator":">=",
    "period":30,
    "cooldown":60,
    "mode":"REACTIVE",
    "type":"WEIGHTED",
    "alarms": [
      {
        "metric":"system.cpu.load[percpu,avg1]",
        "statistic":"avg",
        "comparisonOperator":">",
        "threshold":0.70,
        "weight":1
      }
    ],
    "actions": [
      {
        "type":"SCALE_OUT",
        "value":"2",
        "target":"<target>"
      }
    ]
  }
]
```

This AutoScalePolicy indicates an scaling-out operation of two new VNFC Instances if the averaged value of all measurement results of the metric `cpu load` is greater than the threshold of 0.7 (70%).
This condition is checked every 30 seconds as defined via the period. Once the scaling-out is finished it starts a cooldown of 60 seconds. For this cooldown time further scaling requests are rejected by the AutoScaling System.

The following table describes the meanings of the parameters more in detail.

| Params          				| Meaning       																|
| -------------   				| -------------
| name | This is the human-readable name of the AutoScalePolicy used for identification. |
| threshold | Is a value in percentage that indicates how many sub alarms have to be fired before firing the high-alarm of the AutoScalePolicy. For example, a value of 100 indicates that all sub alarms have to be fired in order to execute the actions of this AutoScalePolicy. |
| comparisonOperator | This comparison operator is used to check the percentages of thrown alarms. 100% means that all weighted alarms must be thrown. 50% would mean that only half of the weighted alarms must be thrown in oder to trigger the scaling action.|
| period | This is the period of checking conditions of AutoScalePolicies. For example, a value of 30 indicates, that every 30 seconds all the conditions of the defined AutoScalePolicy are checked. |
| cooldown | This is the amount of time the VNF needs to wait between two scaling operations to ensure that the executed scaling action takes effect. Further scaling actions that are requested during the cooldown period are rejected. |
| mode | This defines the mode of the AutoScalePolicy. This is mainly about the way of recognizing alarms and conditions, like: `REACTIVE`, `PROACTIVE`, `PREDICTIVE`. At this moment `REACTIVE` is provided only. |
| type | The type defines the meaning and the way of processing alarms. Here we distinguish between `VOTED`, `WEIGHTED`, `SIMPLE`. Currently supported is `WEIGHTED` |
| alarms | The list of alarms defines all the alarms and conditions that belongs to the same AutoScalePolicy. The list of alarms is affected by the mode and the type of the AutoScalePolicy and influences the final check towards the threshold that decides about the triggering of the AutoScalePolicy. Each alarm is composed as defined [here](#alarms). |
| actions | The list of actions defines the actions that shall be executed once the conditions (alarms) of the AutoScalePolicy are met and the corresponding actions of the AutoScalePolicy are triggered. Actions are defined as show [here](#actions). |

#### Alarms

An alarm defines the conditions in order to trigger the the automatic scaling.

| Params          				| Meaning     	|
| -------------   				| -------------
| metric | This is the name of the metric that is considered when checking the conditions, e.g., cpu idle time, memory consumption, network traffic, etc. This metric must be available through the Monitoring System. |
| statistic | This defines the way of calculating the final measurement result over the group of instances. Possible values are: avg, min, max, sum, count. |
| comparisonOperator | The comparisonOperator defines how to compare the final measurement result with the threshold. Possible values are: `=`, `>`, `>=`, `<`, `<=`, `!=`. |
| threshold | The threshold defines the value that is compared with the final measurement of a specific metric. |
| weight | The weight defines the weight of the alarm and is used when combining all the alarms of an AutoScalePolicy to a final indicator that defines how many alarms must be fired. In this way prioritized alarms can be handled with different weights. For example, there is an alarm with the weight of three and another alarm with the weight of one. If the Alarm with weight three is fired and the second one is not fired, the final result would be 75\% in the meaning of three quarters of the conditions are met. |

#### Actions

An Action defines the operation that will be executed (if possible) when the scaling conditions are met that are defined in the Alarms.

| Params          				| Meaning       	|
| -------------   				| -------------
| type | The type defines the type of the action to be executed. For example, `SCALE_OUT` indicates that resources shall be added and `SCALE_IN` means that resources shall be released. Currently provided types of actions are listed [here](#action-types). |
| value | The value is related to the type of action. `SCALE_OUT` and `SCALE_IN` expects a value that defines how many instances should be scaled-out or scaled-in, `SCALE_OUT_TO` and `SCALE_IN_TO` expects a number to what the number of instances shall be scaled in or out. Supported types of actions are shown [here](#action-types) |
| target| [OPTIONAL] The target allows scaling of other VNFs when conditions are met of the considered VNF that includes the policy. The target points to the type of the VNF that should be scaled. If multiple VNFs has the same type, it will be chosen one of them to execute the scaling action. If the target is not defined, it will be executed scaling actions on the same VNF that includes the policy. |

##### Action types

Actions types are the operations that can be executed when defined conditions are met. The following list shows which actions are supported at the moment and what they will do.

| Params          				| Meaning |
| -------------   				| -------------
|SCALE_OUT | scaling-out a specific number of instances |
|SCALE_IN | scaling-in a specific number of instances |
|SCALE_OUT_TO | scaling-out to a specific number of instances |
|SCALE_IN_TO | scaling-in to a specific number of instances |

# Issue tracker

Issues and bug reports should be posted to the GitHub Issue Tracker of this project

# What is Open Baton?

OpenBaton is an open source project providing a comprehensive implementation of the ETSI Management and Orchestration (MANO) specification.

Open Baton is a ETSI NFV MANO compliant framework. Open Baton was part of the OpenSDNCore (www.opensdncore.org) project started almost three years ago by Fraunhofer FOKUS with the objective of providing a compliant implementation of the ETSI NFV specification. 

Open Baton is easily extensible. It integrates with OpenStack, and provides a plugin mechanism for supporting additional VIM types. It supports Network Service management either using a generic VNFM or interoperating with VNF-specific VNFM. It uses different mechanisms (REST or PUB/SUB) for interoperating with the VNFMs. It integrates with additional components for the runtime management of a Network Service. For instance, it provides autoscaling and fault management based on monitoring information coming from the the monitoring system available at the NFVI level.

# Source Code and documentation

The Source Code of the other Open Baton projects can be found [here][openbaton-github] and the documentation can be found [here][openbaton-doc] .

# News and Website

Check the [Open Baton Website][openbaton]
Follow us on Twitter @[openbaton][openbaton-twitter].

# Licensing and distribution
Copyright [2015-2016] Open Baton project

Licensed under the Apache License, Version 2.0 (the "License");

you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# Support
The Open Baton project provides community support through the Open Baton Public Mailing List and through StackOverflow using the tags openbaton.

# Supported by
  <img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/fokus.png" width="250"/><img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/tu.png" width="150"/>

* [NUBOMEDIA][nubomedia]
* [Mobile Cloud Networking][mcn]
* [CogNet][cognet]

[fokus-logo]: https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/fokus.png
[openbaton]: http://openbaton.org
[openbaton-doc]: http://openbaton.org/documentation
[openbaton-github]: http://github.org/openbaton
[openbaton-logo]: https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/openBaton.png
[openbaton-mail]: mailto:users@openbaton.org
[openbaton-twitter]: https://twitter.com/openbaton
[tub-logo]: https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/tu.png
[nubomedia]: https://www.nubomedia.eu/
[mcn]: http://mobile-cloud-networking.eu/site/
[cognet]: http://www.cognet.5g-ppp.eu/cognet-in-5gpp/
