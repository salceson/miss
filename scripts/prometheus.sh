#!/bin/bash -l
## Job name
#SBATCH -J TrafficSim
## Number of nodes to allocate
#SBATCH -N 23
## Number of tasks per node (by default number of cores per node to allocate)
#SBATCH --ntasks-per-node=24
#SBATCH --mem-per-cpu=1GB
#SBATCH --time=00:01:30
## Partition
#SBATCH -p plgrid-testing
#SBATCH --output="results/%j.out"
#SBATCH --error="results/%j.err"

cd ${SLURM_SUBMIT_DIR}

module add plgrid/tools/java8/1.8.0_60

declare -a PARAMS_LIST
#PARAMS_LIST[0]='COLS=4; ROWS=4; AREA_SIZE=4; TRAFFIC=0.7;  NODES=1; CORES=24'
#PARAMS_LIST[0]='COLS=5; ROWS=5; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=24'
#PARAMS_LIST[0]='COLS=6; ROWS=6; AREA_SIZE=4; TRAFFIC=0.7;  NODES=2; CORES=24'
#PARAMS_LIST[0]='COLS=7; ROWS=7; AREA_SIZE=4; TRAFFIC=0.7;  NODES=3; CORES=24'
#PARAMS_LIST[0]='COLS=10; ROWS=10; AREA_SIZE=4; TRAFFIC=0.7;  NODES=5; CORES=24'
#PARAMS_LIST[0]='COLS=12; ROWS=12; AREA_SIZE=4; TRAFFIC=0.7;  NODES=7; CORES=24'
#PARAMS_LIST[0]='COLS=16; ROWS=16; AREA_SIZE=4; TRAFFIC=0.7;  NODES=12; CORES=24'
PARAMS_LIST[0]='COLS=22; ROWS=22; AREA_SIZE=4; TRAFFIC=0.7;  NODES=22; CORES=24'
#PARAMS_LIST[0]='COLS=30; ROWS=30; AREA_SIZE=4; TRAFFIC=0.7;  NODES=40; CORES=24'

SUPERVISOR_HOSTNAME=`/bin/hostname`

HOSTNAMES=()
for HOST in `scontrol show hostnames`;
do
    if [ "$HOST" != "$SUPERVISOR_HOSTNAME" ]; then
        HOSTNAMES+=(${HOST})
    fi
done

mkdir ${SLURM_SUBMIT_DIR}/results/${SLURM_JOB_ID}

for PARAMS in "${PARAMS_LIST[@]}"
do
    echo ${PARAMS}
    eval ${PARAMS}

    ${JAVA_HOME}/bin/java \
        -Dakka.remote.netty.tcp.hostname=${SUPERVISOR_HOSTNAME} \
        -Dtrafficsimulation.warmup.seconds=20 \
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
        srun -w${WORKER_HOST} -c${CORES} -N1 -n1 \
            -o ${SLURM_SUBMIT_DIR}/results/%j/%J.${WORKER_HOST}.out \
            -e ${SLURM_SUBMIT_DIR}/results/%j/%J.${WORKER_HOST}.err \
            ${JAVA_HOME}/bin/java \
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
