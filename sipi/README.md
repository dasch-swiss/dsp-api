# Knora Specific Sipi Scripts and Configurations

This directory holds Knora specific scripts and configurations for [Sipi](https://github.com/dhlab-basel/Sipi).

## Usage

To start Sipi (with config for integration tests), run from inside this folder:

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker image rm --force dhlabbasel/sipi:develop // deletes cached image and needs only to be used when newer image is available on dockerhub
$ docker run --rm -it --add-host webapihost:$DOCKERHOST -v $PWD/config:/sipi/config -v $PWD/scripts:/sipi/scripts -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 dhlabbasel/sipi:develop --config=/sipi/config/sipi.knora-docker-it-config.lua
```

where `LOCAL_IP_ADDRESS` is the IP of the host running the `Knora-Service`.