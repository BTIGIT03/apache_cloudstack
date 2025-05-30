# Docker Files for Apache CloudStack

Dockerfiles used to build CloudStack images are available on Docker hub.

## Using images from docker-hub

### CloudStack Simulator

CloudStack Simulator is an all in one CloudStack Build including the simulator that mimic Hypervisor. This is useful to test CloudStack API behavior without having to deploy real hypervisor nodes. CloudStack Simulator is used for tests and CI.

```
docker pull apache/cloudstack-simulator

or pull it with a particular build tag

docker pull apache/cloudstack-simulator:4.17.2.0

docker run --name simulator -p 8080:5050 -d apache/cloudstack-simulator

or

docker run --name simulator -p 8080:5050 -d apache/cloudstack-simulator:4.17.2.0

```

Access CloudStack UI
```
Open your browser at http://localhost:8080/
Default login is admin:password
```

Deploy a datacenter:
```
# Advanced zone
docker exec -it simulator python /root/tools/marvin/marvin/deployDataCenter.py -i /root/setup/dev/advanced.cfg

# Advanced zone with security groups
docker exec -it simulator python /root/tools/marvin/marvin/deployDataCenter.py -i /root/setup/dev/advancedsg.cfg

# Basic zone
docker exec -it simulator python /root/tools/marvin/marvin/deployDataCenter.py -i /root/setup/dev/basic.cfg
```

Log into the simulator:
```
docker exec -it simulator bash
```

### CloudStack Management-server

```
docker pull mysql:5.5
docker pull cloudstack/management_centos6
docker run --name cloudstack-mysql -e MYSQL_ROOT_PASSWORD=password -d mysql:5.5
docker run -ti --name cloudstack --link cloudstack-mysql:mysql -d -p 8080:8080 -p 8250:8250 cloudstack/management_centos6
```

### Marvin

Use marvin to deploy or test your CloudStack environment.
Use Marvin with cloudstack connection through the API port (8096)

```
docker pull cloudstack/marvin
docker run -ti --rm --name marvin --link simulator:8096 cloudstack/marvin
```

Deploy Cloud using marvin:

```
docker run -ti --rm --link simulator:8096 cloudstack/marvin python /marvin/marvin/deployDataCenter.py -i /marvin/dev/advanced.cfg
```

Perform Smoke tests against CloudStack Simulator container:
```
docker run -ti --rm --link simulator:8096 \
  nosetests-2.7 -v --with-marvin \
  --marvin-config=dev/advanced.cfg \
  --with-xunit \
  --xunit-file=xunit.xml \
  -a tags=advanced,required_hardware=false \
  --zone=Sandbox-simulator \
  --hypervisor=simulator \
  -w integration/smoke
```

# How to build images

Image provided by CloudStack are automatically built by Jenkins performing following tasks:

### CentOS 6

CentOS 6 image use RPM's from jenkins.buildacloud.org
tag:latest = main branch

1. build the base image

   ```
   docker build -f Dockerfile.centos6 -t cloudstack/management_centos6 .
   ```

2. on jenkins, database and systemvm.iso are pre-deployed. the initial start require privileged container to
   mount systemvm.iso and copy ssh_rsa.pub into it.

   ```
   docker run --name cloudstack-mysql -e MYSQL_ROOT_PASSWORD=password -d mysql:5.5
   docker run --privileged --link cloudstack-mysql:mysql -d --name cloudstack cloudstack/management_centos6
   sleep 300
   docker exec cloudstack /etc/init.d/cloudstack-management stop
   docker stop cloudstack
   docker commit -m "init system.iso" -a "Apache CloudStack" cloudstack cloudstack/management_centos6
   ```

### Marvin

Build Marvin container usable to deploy cloud in the CloudStack management server container.

1. to build the image

   ```
   docker build -f Dockerfile.marvin -t cloudstack/marvin ../..
   ```

### Simulator

Build CloudStack with Simulator. This image is an all in one, including the database. Built from source using maven.

```
docker build -f Dockerfile -t cloudstack/simulator ../..
```
