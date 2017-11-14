# Tomcat Session Replication Sample

This sample showcases the Tomcat Session Replication configuration. To simulate a cluster, this samples uses a Docker
Compose file, with two nodes that deploy a simple application that keeps track of the number of times that the 
SessionReplicationServlet was invoked across all Tomcat nodes.

## Running the Sample

Docker is required to run the sample. To run it, just execute in the sample root folder:

```bash
docker-compose up
```

And to stop it:

```bash
docker-compose down
```

## Try it

The sample application should be running in `http://localhost:8081/session-replication/` and `http://localhost:8082/session-replication/`.

If you access both addresses you should see the Session ID and a counter that increases each time a hit is executed in 
any of the nodes. Now you can try to stop one of the nodes with:

```bash
docker stop tomcat-node-1
```

Now if you hit the node 2 a couple of times in `http://localhost:8082/session-replication/` and then start node 1 again:

```bash
docker start tomcat-node-1
```

And access `http://localhost:8082/session-replication/`, you should see that the counter is updated with Session data 
from node 2.

## Configuration

### server.xml

Please see [Clustering/Session Replication HOW-TO - Configuration Example](https://tomcat.apache.org/tomcat-7.0-doc/cluster-howto.html#Configuration_Example)

### logging.properties

Add the following lines to `conf/logging.properties` in the Tomcat distribution folder to turn on the logging around 
Session Replication:

```properties
org.apache.catalina.tribes.MESSAGES.level = FINE
org.apache.catalina.tribes.level = FINE
org.apache.catalina.ha.level = FINE
```

### Application

To be able to use Tomcat Session Replication, the target application needs to comply with a few requirements.

#### web.xml

The application `web.xml` requires the `<distributable/>` element.

#### Session Interaction

Session Replication is only triggered when the Session changes, and for change it means that either `setAttribute` or 
`removeAttribute` methods need to be called in the `HttpSession`. For instance:

```java
final Object data = session.getAttribute("data");
if (data != null) {
    ((Data) data).incrementCounter();
    session.setAttribute("data", data);
} else {
    session.setAttribute("data", new Data());
} 
```

Will replicate the session. However if we do it like this:

```java
final Object data = session.getAttribute("data");
if (data != null) {
    ((Data) data).incrementCounter();
} else {
    session.setAttribute("data", new Data());
} 
```

Replication won't be triggered after initializing the `Data` object since a `setAttribute` or `removeAttribute` method 
is not executed.

#### Serialization

All objects stored in the Session must be `Serializable`.________
