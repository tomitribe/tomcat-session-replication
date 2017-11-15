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

### Tomcat

#### server.xml

In the `Engine` element add the property `jvmRoute`. This needs to be unique for each of the nodes in the cluster and 
it is used to append a name in the Session Id. This name can later be used by the Load Balancer to determine which node 
to use and to keep a Sticky Session when proxying a request. The easiest way is to use a property replacement, so then 
the value can be set using a System Property when starting up the Tomcat instance:

```xml
<Engine name="Catalina" defaultHost="localhost" jvmRoute="${load-balancer.route}">
```

Then `${load-balancer.route}` can be set into an environment variable `CATALINA_OPTS=-Dload-balancer.route=node1`. 
Tomcat will automatically read this environment variable and perform property replacement in the `server.xml`.

 

Add the following XML fragment into `conf/server.xml` inside the `Engine` element:

```xml
<Cluster className="org.apache.catalina.ha.tcp.SimpleTcpCluster"
       channelSendOptions="8">

<Manager className="org.apache.catalina.ha.session.DeltaManager"
         expireSessionsOnShutdown="false"
         notifyListenersOnReplication="true"/>

<Channel className="org.apache.catalina.tribes.group.GroupChannel">
  <Membership className="org.apache.catalina.tribes.membership.McastService"
              address="228.0.0.4"
              port="45564"
              frequency="500"
              dropTime="3000"/>
  <Receiver className="org.apache.catalina.tribes.transport.nio.NioReceiver"
            address="auto"
            port="4000"
            autoBind="100"
            selectorTimeout="5000"
            maxThreads="6"/>

  <Sender className="org.apache.catalina.tribes.transport.ReplicationTransmitter">
    <Transport className="org.apache.catalina.tribes.transport.nio.PooledParallelSender"/>
  </Sender>
  <Interceptor className="org.apache.catalina.tribes.group.interceptors.TcpFailureDetector"/>
  <Interceptor className="org.apache.catalina.tribes.group.interceptors.MessageDispatch15Interceptor"/>
</Channel>

<Valve className="org.apache.catalina.ha.tcp.ReplicationValve"
       filter=""/>
<Valve className="org.apache.catalina.ha.session.JvmRouteBinderValve"/>

<Deployer className="org.apache.catalina.ha.deploy.FarmWarDeployer"
          tempDir="/tmp/war-temp/"
          deployDir="/tmp/war-deploy/"
          watchDir="/tmp/war-listen/"
          watchEnabled="false"/>

<ClusterListener className="org.apache.catalina.ha.session.JvmRouteSessionIDBinderListener"/>
<ClusterListener className="org.apache.catalina.ha.session.ClusterSessionListener"/>
</Cluster>
```

Note that the Cluster Membership is done using Multicast in the `Membership` element. In this case, the cluster is 
using the address `228.0.0.4` on port `45564`. Make sure that all the nodes that you wish to belong to the same cluster 
use the same configuration and are able to reach each other. When you have different environments on the same network 
and you want to set different clusters, the address and port should be changed to avoid members joining a cluster that 
they don't belong.

The membership component broadcasts TCP address/port of itself to the other nodes so that communication between nodes 
can be done over TCP. This is set up in the `Receiver` element. Make sure that all the nodes that belong to the same 
cluster are able to reach each other and connect to the `Receiber` port.

#### logging.properties

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
