#!/bin/bash -l
## Job name
#SBATCH -J TrafficSim
## Number of nodes to allocate
#SBATCH -N 7
## Number of tasks per node (by default number of cores per node to allocate)
#SBATCH --ntasks-per-node=24
#SBATCH --mem-per-cpu=1GB
#SBATCH --time=00:12:00
## Partition
#SBATCH -p plgrid-testing
#SBATCH --output="%j.out"
#SBATCH --error="%j.err"

cd ${SLURM_SUBMIT_DIR}

module add plgrid/tools/java8/1.8.0_60

declare -a PARAMS_LIST
PARAMS_LIST[0]='COLS=7; ROWS=7; AREA_SIZE=4; TRAFFIC=0.7;  NODES=3; CORES=24'
PARAMS_LIST[1]='COLS=6; ROWS=6; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=24'
PARAMS_LIST[2]='COLS=5; ROWS=5; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=18'
PARAMS_LIST[3]='COLS=4; ROWS=4; AREA_SIZE=4; TRAFFIC=0.7;  NODES=1; CORES=24'
PARAMS_LIST[4]='COLS=3; ROWS=3; AREA_SIZE=4; TRAFFIC=0.7;  NODES=1; CORES=15'
PARAMS_LIST[5]='COLS=2; ROWS=2; AREA_SIZE=4; TRAFFIC=0.7;  NODES=1; CORES=8'
PARAMS_LIST[6]='COLS=1; ROWS=1; AREA_SIZE=4; TRAFFIC=0.7;  NODES=1; CORES=3'

SUPERVISOR_HOSTNAME=`/bin/hostname`

HOSTNAMES=()
for HOST in `scontrol show hostnames`;
do
    if [ "$HOST" != "$SUPERVISOR_HOSTNAME" ]; then
        HOSTNAMES+=(${HOST})
    fi
done

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
        -Dakka.loglevel=WARNING \
        -jar ${SLURM_SUBMIT_DIR}/supervisor.jar &
    SUPERVISOR_PID=$!

    sleep 15

    for WORKER_HOST in ${HOSTNAMES[@]:0:${NODES}};
    do
        srun -w${WORKER_HOST} -l -N1 -n1 ${JAVA_HOME}/bin/java \
            -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME} \
            -Dakka.remote.netty.tcp.hostname=${WORKER_HOST} \
            -Dakka.remote.log-remote-lifecycle-events=off \
            -Dakka.loglevel=WARNING \
            -jar ${SLURM_SUBMIT_DIR}/worker.jar &
    done

    wait ${SUPERVISOR_PID}

    sleep 10
done
exit 0
