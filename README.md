# Tomcat Session Replication Sample

This sample showcases the Tomcat Session Replication configuration. To simulate a Tomcat cluster, this samples uses a 
Docker Compose file, with two nodes that deploy a simple application to keeps track of the number of times that the 
SessionReplicationServlet was invoked across all Tomcat nodes.

Additionally, it also includes an instance of Httpd to Load Balance between both the Tomcat nodes.

## Running the Sample

Maven and Docker are required to run the sample. To run it, just execute in the sample root folder:

```bash
mvn clean install
```

```bash
docker-compose up
```

And to stop it:

```bash
docker-compose down
```

## Try it

### Tomcat

The sample application should be running in `http://localhost:8081/session-replication/` and `http://localhost:8082/session-replication/`.

If you access both addresses you should see the Session ID and a Counter that increases each time a hit is executed in 
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

### Httpd

The Httpd Load Balancer is available in `http://localhost:8080/`. This will load balance the application between the two 
Tomcat nodes. It also uses a Sticky Session to keep the current user in the same node if possible.

Also available in `http://localhost/balancer-manager` is the Httpd Load Balancer Manager. By using this address, is it 
possible to check several stats about the Load Balancer.

When using the Httpd Load Balancer to simulate a failover, due to the nature of Docker and a bug in Httpd 
(https://bz.apache.org/bugzilla/show_bug.cgi?id=54657), stopping one of the Tomcat Docker containers won't work 
correctly, since Httpd will be stuck in a 502 and unable to perform a DNS lookup to the balancing member.

There is still a way to simulate a failure, by using the Httpd Load Balancer Manager in 
`http://localhost/balancer-manager`. Just click the `Worker URL` in the page for the Tomcat instance you wish to remove
from the Load Balancer, and hit `On` on the `Disabled` section and then `Submit`.

Try accessing `http://localhost:8080/` again and the Load Balancer should send the request to the remaining node.    

## Configuration

### server.xml



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

All objects stored in the Session must be `Serializable`.

## Additional Resources

* [Clustering/Session Replication HOW-TO - Configuration Example](https://tomcat.apache.org/tomcat-7.0-doc/cluster-howto.html#Configuration_Example)
