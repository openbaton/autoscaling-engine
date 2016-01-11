#!/bin/bash
source gradle.properties

_version=${version}
_level=info
_openbaton_config_file=/etc/openbaton/cli.properties

function usage {
    echo -e "Open-Baton\n"
    echo -e "Usage:\n\t ./openbaton.sh [option] comand [comand] [comand]\n\t"
    echo -e "where option is"
    echo -e "\t\t * -c path of properties file"
    echo -e "\t\t * -d debug mode"
    echo -e "comand help to see the list of the comands"
    echo -e "./openbaton.sh help"
}

##
#   MAIN
##

while getopts “:c:d” OPTION
do
     case $OPTION in
         c)
             _openbaton_config_file=$OPTARG
             #echo $_openbaton_config_file
             ;;
         d)
             _level=debug
             ;;
     esac
done
shift $(( OPTIND - 1 ))


if [ $# -eq 0 ]
then
        usage
        exit 1
else

        #cd cli/build/libs/
        java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=$_level "cli/build/libs/cli-all-$_version.jar" $_openbaton_config_file $1 $2 $3
fi




