# Knora Specific Sipi Scripts and Configurations

This directory holds Knora specific scripts and configurations for [Sipi](https://github.com/dhlab-basel/Sipi).

## Usage

To start Sipi, run from inside this folder:

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker run -d --add-host webapihost:$DOCKERHOST -v $PWD/config:/sipi/config -v $PWD/scripts:/sipi/scripts -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 dhlabbasel/sipi:develop /sipi/local/bin/sipi --config=/sipi/config/sipi.knora-docker-config.lua
```

where `LOCAL_IP_ADDRESS` is the IP of the host running the `Knora-Service`.