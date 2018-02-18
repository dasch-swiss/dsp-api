### Monitoring

The monitoring of Knora is based on Prometheus. To run the monitoring stack:

```
$ WEBAPIHOST=<YourLocalIP> ADMIN_USER=admin ADMIN_PASSWORD=admin docker-compose up -d
```

`<YourLocalIP>` is the IP address of your local machine, if you are running `webbapi` outside of
docker. If you are running `webapi` inside a docker container, then connect this container to the
`monitor-net` network and put here instead the name of the container.  

To enable the proemetheus reporter inside `webapi`, start the server with the `-p` flag, or set
the configuration key `app.monitoring.prometheus-reporter` in `application.conf` to `true`.
