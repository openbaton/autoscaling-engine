#!/bin/bash

_openbaton_base="/opt/openbaton"
_autoscaling_engine_base="${_openbaton_base}/autoscaling"
_autoscaling_engine_config_file="/etc/openbaton/autoscaling.properties"

source ./gradle.properties

_version=${version}

function check_already_running {
        result=$(screen -ls | grep autoscaling-engine | wc -l);
        if [ "${result}" -ne "0" ]; then
                echo "AutoScaling-Engine is already running... Attach to screen via \"screen -r autoscaling-engine\""
		exit;
        fi
}

function start {
    check_already_running
    if [ ! -d build/  ]
        then
            compile
    fi
    screen -c screenrc -d -m -S autoscaling-engine -t autoscaling-engine java -jar "build/libs/autoscaling-engine-${_version}.jar" --spring.config.location=file:${_autoscaling_engine_config_file}
    echo "Starting AutoScaling-Engine..."
}

function stop {
    if screen -list | grep "autoscaling-engine"; then
	    #screen -S autoscaling-engine -p 0 -X stuff "exit$(printf \\r)"
	    screen -ls | grep autoscaling-engine | cut -d. -f1 | awk '{print $1}' | xargs kill
    else
        echo "AutoScaling-Engine is not running..."
    fi
}

function restart {
    kill
    start
}


function kill {
    if screen -list | grep "autoscaling-engine"; then
	    screen -ls | grep autoscaling-engine | cut -d. -f1 | awk '{print $1}' | xargs kill
    else
        echo "AutoScaling-Engine is not running..."
    fi
}


function compile {
    ./gradlew build -x test
}

function tests {
    ./gradlew test
}

function clean {
    ./gradlew clean
}

function end {
    exit
}
function usage {
    echo -e "AutoScaling-Engine\n"
    echo -e "Usage:\n\t ./ms-vnfm.sh [compile|start|stop|test|kill|clean]"
}

##
#   MAIN
##

if [ $# -eq 0 ]
   then
        usage
        exit 1
fi

declare -a cmds=($@)
for (( i = 0; i <  ${#cmds[*]}; ++ i ))
do
    case ${cmds[$i]} in
        "clean" )
            clean ;;
        "sc" )
            clean
            compile
            start ;;
        "start" )
            start ;;
        "stop" )
            stop ;;
        "restart" )
            restart ;;
        "compile" )
            compile ;;
        "kill" )
            kill ;;
        "test" )
            tests ;;
        * )
            usage
            end ;;
    esac
    if [[ $? -ne 0 ]];
    then
	    exit 1
    fi
done

