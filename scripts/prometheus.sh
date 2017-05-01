#!/bin/bash -l
## Job name
#SBATCH -J TrafficSim
## Number of nodes to allocate
#SBATCH -N 2
## Number of tasks per node (by default number of cores per node to allocate)
#SBATCH --ntasks-per-node=24
#SBATCH --mem-per-cpu=1GB
#SBATCH --time=00:01:30
## Partition
#SBATCH -p plgrid-short
#SBATCH --output="results/%j.out"
#SBATCH --error="results/%j.err"

cd ${SLURM_SUBMIT_DIR}

module add plgrid/tools/java8/1.8.0_60

declare -a PARAMS_LIST
PARAMS_LIST[0]='COLS=4; ROWS=6; AREA_SIZE=2; TRAFFIC=0.08; ROAD_CELLS=25; NODES=1; APN=24'
#PARAMS_LIST[1]='COLS=6; ROWS=8; AREA_SIZE=2; TRAFFIC=0.08; ROAD_CELLS=25; NODES=2; APN=24'


SUPERVISOR_HOSTNAME=`/bin/hostname`

HOSTNAMES=()
for HOST in `scontrol show hostnames`;
do
    if [ "$HOST" != "$SUPERVISOR_HOSTNAME" ]; then
        HOSTNAMES+=(${HOST})
    fi
done

mkdir ${SLURM_SUBMIT_DIR}/results/${SLURM_JOB_ID}

for i in "${!PARAMS_LIST[@]}"
do
    PARAMS="${PARAMS_LIST[$i]}"
    echo ${PARAMS}
    eval ${PARAMS}

    mkdir ${SLURM_SUBMIT_DIR}/results/${SLURM_JOB_ID}/${i}

    ${JAVA_HOME}/bin/java \
        -Dakka.remote.netty.tcp.hostname=${SUPERVISOR_HOSTNAME} \
        -Dtrafficsimulation.warmup.seconds=20 \
        -Dtrafficsimulation.time.seconds=20 \
        -Dtrafficsimulation.city.cols=${COLS} \
        -Dtrafficsimulation.city.rows=${ROWS} \
        -Dtrafficsimulation.area.size=${AREA_SIZE} \
        -Dtrafficsimulation.area.traffic_density=${TRAFFIC} \
        -Dtrafficsimulation.area.cells_between_intersections=${ROAD_CELLS} \
        -Dworker.nodes=${NODES} \
        -Dworker.areas_per_node=${APN} \
        -Dakka.remote.log-remote-lifecycle-events=off \
        -Dakka.loglevel=INFO \
        -jar ${SLURM_SUBMIT_DIR}/supervisor.jar &
    SUPERVISOR_PID=$!

    for WORKER_HOST in ${HOSTNAMES[@]:0:${NODES}};
    do
        srun -w${WORKER_HOST} -c${SLURM_NTASKS_PER_NODE} -N1 -n1 \
            -o ${SLURM_SUBMIT_DIR}/results/%j/${i}/%J.${WORKER_HOST}.out \
            -e ${SLURM_SUBMIT_DIR}/results/%j/${i}/%J.${WORKER_HOST}.err \
            ${JAVA_HOME}/bin/java \
            -Dsupervisor.hostname=${SUPERVISOR_HOSTNAME} \
            -Dakka.remote.netty.tcp.hostname=${WORKER_HOST} \
            -Dakka.remote.log-remote-lifecycle-events=off \
            -Dakka.loglevel=INFO \
            -jar ${SLURM_SUBMIT_DIR}/worker.jar &
    done

    wait ${SUPERVISOR_PID}

    sleep 10
done
exit 0
