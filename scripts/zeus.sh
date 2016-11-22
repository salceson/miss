#!/bin/bash
#PBS -q l_short
#PBS -l nodes=64:ppn=12
#PBS -l walltime=00:10:00

module add plgrid/tools/java8/oracle/1.8.0

SUPERVISOR_HOSTNAME=`hostname`
REMOTE_HOSTS=`cat ${PBS_NODEFILE} | uniq | grep -v ${SUPERVISOR_HOSTNAME}`

declare -a PARAMS_LIST
PARAMS_LIST[0]='COLS=7; ROWS=7; AREA_SIZE=4; TRAFFIC=0.7;  NODES=63; CORES=1'
PARAMS_LIST[1]='COLS=6; ROWS=6; AREA_SIZE=4; TRAFFIC=0.7;  NODES=48; CORES=1'
PARAMS_LIST[2]='COLS=5; ROWS=5; AREA_SIZE=4; TRAFFIC=0.7;  NODES=35; CORES=1'
PARAMS_LIST[3]='COLS=4; ROWS=4; AREA_SIZE=4; TRAFFIC=0.7;  NODES=24; CORES=1'
PARAMS_LIST[4]='COLS=3; ROWS=3; AREA_SIZE=4; TRAFFIC=0.7;  NODES=15; CORES=1'
PARAMS_LIST[5]='COLS=2; ROWS=2; AREA_SIZE=4; TRAFFIC=0.7;  NODES=8; CORES=1'
PARAMS_LIST[6]='COLS=1; ROWS=1; AREA_SIZE=4; TRAFFIC=0.7;  NODES=3; CORES=1'

for PARAMS in "${PARAMS_LIST[@]}"
do
    echo ${PARAMS}
    eval ${PARAMS}

    ${JAVA_HOME}/bin/java \
        -Dakka.remote.netty.tcp.hostname=${SUPERVISOR_HOSTNAME} \
        -Dtrafficsimulation.time.seconds=20 \
        -Dtrafficsimulation.city.cols=${COLS} \
        -Dtrafficsimulation.city.rows=${ROWS} \
        -Dtrafficsimulation.area.size=${AREA_SIZE} \
        -Dtrafficsimulation.area.traffic_density=${TRAFFIC} \
        -Dworker.nodes=${NODES} \
        -Dworker.cores=${CORES} \
        -Dakka.remote.log-remote-lifecycle-events=off \
        -jar ${PBS_O_WORKDIR}/supervisor.jar &
    SUPERVISOR_PID=$!

    sleep 15

    for WORKER_HOST in ${REMOTE_HOSTS}
    do
        pbsdsh -c 1 -h ${WORKER_HOST} ${JAVA_HOME}/bin/java \
            -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME} \
            -Dakka.remote.netty.tcp.hostname=${WORKER_HOST} \
            -Dakka.remote.log-remote-lifecycle-events=off \
            -jar ${PBS_O_WORKDIR}/worker.jar &
    done

    wait ${SUPERVISOR_PID}

done
exit 0
