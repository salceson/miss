#!/bin/bash
#PBS -q q_short
#PBS -l nodes=3:ppn=4
#PBS -l walltime=00:02:00

SUPERVISOR_HOSTNAME=`hostname`
REMOTE_HOSTS=`cat ${PBS_NODEFILE} | uniq | grep -v ${SUPERVISOR_HOSTNAME}`

java -jar supervisor.jar &
SUPERVISOR_PID=$!

for WORKER_HOST in ${REMOTE_HOSTS}
do
    pbsdsh -h ${WORKER_HOST} java -jar worker.jar -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME}
done

fg ${SUPERVISOR_PID}
exit 0
