#!/bin/bash
#PBS -q l_short
#PBS -l nodes=3:ppn=12
#PBS -l walltime=00:10:00

module add plgrid/tools/java8/oracle/1.8.0

SUPERVISOR_HOSTNAME=`hostname`
REMOTE_HOSTS=`cat ${PBS_NODEFILE} | uniq | grep -v ${SUPERVISOR_HOSTNAME}`

${JAVA_HOME}/bin/java -Dakka.remote.netty.tcp.hostname=${SUPERVISOR_HOSTNAME} \
    -Dtrafficsimulation.time.seconds=20 \
    -Dtrafficsimulation.city.cols=4 \
    -Dtrafficsimulation.city.rows=4 \
    -Dworker.nodes=2 \
    -Dworker.cores=12 \
    -Dakka.remote.log-remote-lifecycle-events=off \
    -jar ${PBS_O_WORKDIR}/supervisor.jar &
SUPERVISOR_PID=$!

sleep 15

for WORKER_HOST in ${REMOTE_HOSTS}
do
    pbsdsh -c 1 -h ${WORKER_HOST} ${JAVA_HOME}/bin/java -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME} \
        -Dakka.remote.netty.tcp.hostname=${WORKER_HOST} \
        -Dakka.remote.log-remote-lifecycle-events=off \
        -jar ${PBS_O_WORKDIR}/worker.jar &
done

wait ${SUPERVISOR_PID}
exit 0
