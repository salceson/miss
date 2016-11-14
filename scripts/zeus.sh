#!/bin/bash
#PBS -q q_short
#PBS -l nodes=3:ppn=4
#PBS -l walltime=00:02:00

module load plgrid/tools/java8/oracle/1.8.0

SUPERVISOR_HOSTNAME=`hostname`
REMOTE_HOSTS=`cat ${PBS_NODEFILE} | uniq | grep -v ${SUPERVISOR_HOSTNAME}`

java -Dakka.remote.netty.tcp.hostname=${SUPERVISOR_HOSTNAME} \
-Dtrafficsimulation.time.seconds=20 \
-Dworker.nodes=2 \
-Dworker.cores=4 \
-jar supervisor.jar &

for WORKER_HOST in ${REMOTE_HOSTS}
do
    pbsdsh -h ${WORKER_HOST} java -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME} \
    -Dakka.remote.netty.tcp.hostname=${WORKER_HOST} \
    -jar worker.jar
done

fg
exit 0
