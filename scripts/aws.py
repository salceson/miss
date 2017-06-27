#!/usr/bin/env python
# coding: utf-8
import argparse
import fcntl
import socket
import struct
import subprocess

from boto import ec2


# This script assumes following things:
# * It can run the worker on any of your EC2 instances (besides itself)
# * The instances are based on Ubuntu
# * You have key named `Ubuntu.pem` in your home directory
# * You have AWS credentials in ~/.aws directory
# * You have installed boto (`pip install boto`)


def get_ip_address(interface_name):
    """Gets IP address for interface interface_name.

    :type interface_name: str
    :arg interface_name: interface name
    :rtype: str
    :return: IP address
    """
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    address = socket.inet_ntoa(fcntl.ioctl(
        s.fileno(),
        0x8915,  # SIOCGIFADDR
        struct.pack('256s', interface_name[:15])
    )[20:24])
    s.close()
    return address


def flatten(l):
    """Flattens list l.

    :type l: list
    :arg l: list to flatten
    :rtype: list
    :return: flattened list
    """
    return [item for sublist in l for item in sublist]


def get_instances_ips():
    """Gets private IP addresses for all running EC2 instances.

    :rtype: list
    :return: list of private IP addresses of running EC2 instances
    """
    conn = ec2.connect_to_region('eu-west-1')
    instances = conn.get_all_instances()
    conn.close()
    instances = flatten(list(map(lambda x: x.instances, instances)))
    ips = list(map(lambda x: x.private_ip_address, instances))
    ips = list(filter(lambda x: x is not None, ips))
    ips = list(map(str, ips))
    return ips


def construct_supervisor_command_line(supervisor_ip, cols, rows, area_size, traffic, road_cells, nodes, apn):
    """Creates the command to start up the supervisor.

    :type supervisor_ip: str
    :param supervisor_ip: the private IP address of supervisor
    :type cols: int
    :param cols: city cols
    :type rows: int
    :param rows: city rows
    :type area_size: int
    :param area_size: size of the area
    :type traffic: float
    :param traffic: traffic density
    :type road_cells: int
    :param road_cells: road cells
    :type nodes: int
    :param nodes: nodes number
    :type apn: int
    :param apn: areas per node
    :rtype: str
    :return: command line to start up the supervisor
    """
    command_line = [
        'java',
        '-Dakka.remote.netty.tcp.hostname=' + supervisor_ip,
        '-Dtrafficsimulation.warmup.seconds=20',
        '-Dtrafficsimulation.time.seconds=20',
        '-Dtrafficsimulation.city.cols=' + str(cols),
        '-Dtrafficsimulation.city.rows=' + str(rows),
        '-Dtrafficsimulation.area.size=' + str(area_size),
        '-Dtrafficsimulation.area.traffic_density=%.2f' % traffic,
        '-Dtrafficsimulation.area.cells_between_intersections=' + str(road_cells),
        '-Dworker.nodes=' + str(nodes),
        '-Dworker.areas_per_node=' + str(apn),
        '-Dakka.remote.log-remote-lifecycle-events=off',
        '-Dakka.loglevel=INFO',
        '-jar',
        '/home/ubuntu/supervisor.jar'
    ]
    return ' '.join(command_line)


def construct_worker_command_line(supervisor_ip, worker_ip):
    """Creates the command line to start up the worker on node with IP worker_ip.

    :type supervisor_ip: str
    :param supervisor_ip: the supervisor private IP address
    :type worker_ip: str
    :param worker_ip: the worker private IP address
    :rtype: str
    :return: the command line to start up the worker on node with IP worker_ip
    """
    command_line = [
        'java',
        '-Dsupervisor.hostname=' + supervisor_ip,
        '-Dakka.remote.netty.tcp.hostname=' + worker_ip,
        '-Dakka.remote.log-remote-lifecycle-events=off',
        '-Dakka.loglevel=INFO',
        '-jar',
        '/home/ubuntu/worker.jar',
        '> /home/ubuntu/worker-' + worker_ip + '.out',
        '&'
    ]
    ssh_command_line = [
        'ssh',
        '-o',
        'UserKnownHostsFile=/dev/null',
        '-o',
        'StrictHostKeyChecking=no',
        '-i',
        '/home/ubuntu/Ubuntu.pem',
        'ubuntu@' + worker_ip,
        '\'' + ' '.join(command_line) + '\''
    ]
    return ' '.join(ssh_command_line)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Starts up simulation')
    parser.add_argument('--cols', '-c', type=int, help='Number of city columns', default=2, dest='cols')
    parser.add_argument('--rows', '-r', type=int, help='Number of city rows', default=2, dest='rows')
    parser.add_argument('--area_size', '-s', type=int, help='Area size', default=4, dest='area_size')
    parser.add_argument('--traffic', '-t', type=float, help='Traffic density', default=0.7, dest='traffic')
    parser.add_argument('--road_cells', '-l', type=int, help='Road cells', default=30, dest='road_cells')
    parser.add_argument('--nodes', '-n', type=int, help='Nodes number', default=2, dest='nodes')
    parser.add_argument('--apn', '-p', type=int, help='Areas per node', default=2, dest='apn')
    args = parser.parse_args()

    cols = args.cols
    rows = args.rows
    area_size = args.area_size
    traffic = args.traffic
    road_cells = args.road_cells
    nodes = args.nodes
    apn = args.apn

    if apn * nodes < rows * cols:
        raise ValueError('nodes * apn must be at least the city size (rows * cols)')

    print 'Getting all instances ips...'
    instances_ips = get_instances_ips()
    print 'Done, ips:', instances_ips

    if len(instances_ips) < nodes:
        raise ValueError('Not enough instances')

    print 'Getting supervisor ip...'
    supervisor_ip = get_ip_address('eth0')  # eth0 on AWS, wlp5s0 on my PC
    print 'Done, supervisor ip:', supervisor_ip

    print 'Running supervisor...'
    supervisor_output = open('/home/ubuntu/supervisor.out', 'w')
    supervisor_command_line = construct_supervisor_command_line(supervisor_ip, cols, rows, area_size, traffic,
                                                                road_cells, nodes, apn)
    print 'Executing following command:', supervisor_command_line
    supervisor_popened = subprocess.Popen(supervisor_command_line, stdout=supervisor_output, shell=True)
    print 'Done'

    print 'Running workers...'
    worker_ips = list(filter(lambda x: x != supervisor_ip, instances_ips))
    print 'Worker ips:', worker_ips

    i = 1
    needed_hosts = rows * cols / apn

    for worker_ip in worker_ips:
        if i > needed_hosts:
            break
        print 'Starting actor system on worker:', worker_ip
        worker_command_line = construct_worker_command_line(supervisor_ip, worker_ip)
        print 'Executing following command:', worker_command_line
        subprocess.Popen(worker_command_line, shell=True).wait()
        i += 1
    print 'Done'

    print 'Waiting for supervisor...'
    supervisor_popened.wait()
    print 'Done'

    supervisor_output.close()
