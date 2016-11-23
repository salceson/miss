#!/bin/bash
#PBS -q l_short
#PBS -l nodes=5:ppn=12
#PBS -l walltime=00:10:00

module add plgrid/tools/java8/oracle/1.8.0

SUPERVISOR_HOSTNAME=`hostname`
read -r -a REMOTE_HOSTS <<< `cat ${PBS_NODEFILE} | uniq | grep -v ${SUPERVISOR_HOSTNAME}`

declare -a PARAMS_LIST
PARAMS_LIST[0]='COLS=6; ROWS=6; AREA_SIZE=4; TRAFFIC=0.7;  NODES=4; CORES=12'
PARAMS_LIST[1]='COLS=5; ROWS=5; AREA_SIZE=4; TRAFFIC=0.7;  NODES=3; CORES=12'
PARAMS_LIST[2]='COLS=4; ROWS=4; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=12'
PARAMS_LIST[3]='COLS=3; ROWS=3; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=12'
PARAMS_LIST[4]='COLS=2; ROWS=2; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=12'
PARAMS_LIST[5]='COLS=1; ROWS=1; AREA_SIZE=4; TRAFFIC=0.7;  NODES=1; CORES=12'

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

    for WORKER_HOST in ${REMOTE_HOSTS[@]:0:$NODES}
    do
        pbsdsh -c 1 -h ${WORKER_HOST} ${JAVA_HOME}/bin/java \
            -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME} \
            -Dakka.remote.netty.tcp.hostname=${WORKER_HOST} \
            -Dakka.remote.log-remote-lifecycle-events=off \
            -jar ${PBS_O_WORKDIR}/worker.jar &
    done

    wait ${SUPERVISOR_PID}

    sleep 10
done
exit 0
