# Tomcat Session Replication Sample

This sample showcases the Tomcat / TomEE Session Replication configuration. To simulate a Tomcat / TomEE cluster, this sample uses a
Docker Compose file, with two nodes that deploy a simple application to keep track of the number of times that the 
SessionReplicationServlet was invoked across all Tomcat / TomEE nodes.

Additionally, it also includes an instance of Httpd to Load Balance between both the Tomcat / TomEE nodes.

## Running the Sample

Maven and Docker are required to run the sample. To run it, just go to the project root folder and execute:

```bash
mvn clean install
```

Then chose which environment you want to execute and enter the folder:
```bash
cd tomcat
cd tomcat_no_session_replication
cd tomee
cd tomee_no_session_replication
```

And then:

```bash
docker-compose up
```

And to stop it:

```bash
docker-compose down
```

### Race Conditions
Sometimes the sample might not work properly since both Tomcat instances run at the same time and they don't wait for 
each other when establishing the Cluster. If this is the case, run the sample again.

## Try it

### Tomcat / TomEE

The sample application should be running in `http://localhost:8081/session-replication/` and `http://localhost:8082/session-replication/`.

If you access both addresses you should see the Session ID and a Counter that increases each time a hit is executed in 
any of the nodes. Now you can try to stop one of the nodes with:

```bash
docker stop tomcat-node-1 (Check the right name in the docker-compose.yaml)
```

Now if you hit the node 2 a couple of times in `http://localhost:8082/session-replication/` and then start node 1 again:

```bash
docker start tomcat-node-1 (Check the right name in the docker-compose.yaml)
```

And access `http://localhost:8081/session-replication/`, you should see that the counter is updated with Session data 
from node 2.

### Httpd

The Httpd Load Balancer is available in `http://localhost:8080/`. This will load balance the application between the two 
Tomcat nodes. It also uses a Sticky Session to keep the current user in the same node if possible.

Also available in `http://localhost/balancer-manager` is the Httpd Load Balancer Manager. By using this address, it is 
possible to check several stats about the Load Balancer.

When using the Httpd Load Balancer to simulate a failover, due to the nature of Docker and a bug in Httpd 
(https://bz.apache.org/bugzilla/show_bug.cgi?id=54657), stopping one of the Tomcat Docker containers won't work 
correctly, since Httpd will be stuck in a 502 error status and unable to perform a DNS lookup to the balancing member. 
In this case, due to the bug, Httpd is not able to remove the failing member from the Load Balancer group.

There is still a way to simulate a failure, by using the Httpd Load Balancer Manager in 
`http://localhost/balancer-manager`. Just click the `Worker URL` in the page for the Tomcat instance you wish to remove
from the Load Balancer, and hit `On` in the `Disabled` section and then `Submit`.

Try accessing `http://localhost:8080/` again and the Load Balancer should send the request to the remaining node.    

## Configuration

### Httpd

#### httpd.conf

Considering a fresh install of `httpd`, the following modules of `httpd` need to be activated in the `conf\httpd.conf` 
file:

* `LoadModule proxy_module modules/mod_proxy.so`
* `LoadModule proxy_http_module modules/mod_proxy_http.so`
* `LoadModule proxy_balancer_module modules/mod_proxy_balancer.so`
* `LoadModule lbmethod_byrequests_module modules/mod_lbmethod_byrequests.so`
* `LoadModule slotmem_shm_module modules/mod_slotmem_shm.so`

Then add the following configuration into `conf\httpd.conf` (or in a new conf file included in `conf\httpd.conf`):

```
<Proxy balancer://tomcat>
    BalancerMember http://tomcat-node-1:8080 route=node1 ping=500ms
    BalancerMember http://tomcat-node-2:8080 route=node2 ping=500ms
</Proxy>

ProxyPass /session-replication balancer://tomcat/session-replication/ stickysession=JSESSIONID
ProxyPassReverse /session-replication balancer://tomcat/session-replication/ stickysession=JSESSIONID

<Location "/balancer-manager">
    SetHandler balancer-manager
</Location>
```

#### Sticky Sessions

Sticky Session are advisable to use with the Delta Tomcat Session Replication for the following reasons:

* If a browser is issuing parallel requests, these may be handled by different nodes in the cluster and changes to the 
session might not be visible across all nodes.
* Without Sticky Sessions the replication would require a synchronous approach to ensure that all other nodes in the 
cluster have the session updates before the next request. This would cause performance issues.
* For Debugging purposes, it would be harder to determine what went wrong if each request goes to a different node.

### Tomcat

#### server.xml

In the `Engine` element add the property `jvmRoute`. This needs to be unique for each of the nodes in the cluster and 
it is used to append a name in the Session ID. This name is later used by the Load Balancer to determine which node 
to use and to keep a Sticky Session when proxying a request. To set this value, the easiest way is to use a property replacement configuration, so then the value can be set using a System Property when starting up the Tomcat instance:

```xml
<Engine name="Catalina" defaultHost="localhost" jvmRoute="${load-balancer.route}">
```

Then `${load-balancer.route}` can be set into an environment variable `CATALINA_OPTS=-Dload-balancer.route=node1`. 
Tomcat will automatically read this environment variable and perform property replacement in the `server.xml`. Adjust to 
each node with the right route value.

Also, add the following XML fragment into `conf/server.xml` inside the `Engine` element:

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
using the address `228.0.0.4` on port `45564`. Make sure that all the nodes that go into the same cluster use the same 
configuration and are able to reach each other. When you have different environments on the same network and you want 
to set different clusters, the address and port should be changed to avoid members joining a cluster that they don't belong.

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

## Step by Step

### Scenario 1

Consider the following Scenario:

1. Tomcat **A** and **B** starts up and form a Cluster
2. Tomcat **A** receives and serves a request with Session **S1**
3. Tomcat **A** receives and serves another request for Session **S1**
4. Tomcat **A** crashes
5. Tomcat **B** receives and serves a request for Session **S1**
6. Tomcat **A** starts up again
7. Tomcat **B** crashes
8. Tomcat **A** receives and serves a request for Session **S1**

#### Step 1
Nothing out of the ordinary, both Tomcat nodes start up and form a Cluster.

#### Step 2
Tomcat **A** receives a request and creates Session **S1**:

```
tomcat-node-1    | FINE: Manager [localhost#/session-replication] send new session (4CF1497FA5FEB5CD187F0FF21A3769B8.node1)
tomcat-node-1    | FINE: Created a DeltaSession with Id [4CF1497FA5FEB5CD187F0FF21A3769B8.node1] Total count=1
```

And Tomcat **B** receives it:

```
tomcat-node-2    | FINE: Assuming clocks are synched: Replication for 4CF1497FA5FEB5CD187F0FF21A3769B8.node1-1510773649191 took=14 ms.
```
#### Step 3

When the Session changes in Tomcat **A** it sends a Delta Request to the Cluster:

```
tomcat-node-1    | FINE: Manager [localhost#/session-replication]: create session message [A97DD384040BD3557F9F17182868A45A.node1] delta request.
```

And Tomcat **B** receives it:

```
tomcat-node-2    | FINE: Assuming clocks are synched: Replication for A97DD384040BD3557F9F17182868A45A.node1-1510773759604 took=6 ms.
tomcat-node-2    | FINE: Manager [localhost#/session-replication]: received session [A97DD384040BD3557F9F17182868A45A.node1] delta.
```

#### Step 4
Tomcat **A** crashes the cluster continues as normal.

#### Step 5
A new request for Session **S1** is made and this time Tomcat **B** needs to process it. It detects the failover and 
renames the Session to use the Tomcat **B** route:

````
tomcat-node-2    | FINE: Detected a failover with different jvmRoute - orginal route: [node1] new one: [node2] at session id [A97DD384040BD3557F9F17182868A45A.node1]
tomcat-node-2    | FINE: Set Orginal Session id at request attribute org.apache.catalina.ha.session.JvmRouteOrignalSessionID value: A97DD384040BD3557F9F17182868A45A.node1
tomcat-node-2    | FINE: Changed session from [A97DD384040BD3557F9F17182868A45A.node1] to [A97DD384040BD3557F9F17182868A45A.node2]
````

#### Step 6
Tomcat **A** starts up again. It request the current Session state before serving any request from the Cluster:

```
tomcat-node-1    | INFO: Manager [localhost#/session-replication], requesting session state from org.apache.catalina.tribes.membership.MemberImpl[tcp://{172, 18, 0, 3}:4000,{172, 18, 0, 3},4000, alive=424292, securePort=-1, UDP Port=-1, id={87 34 -54 -91 81 34 69 -16 -90 34 -122 24 84 -84 -128 -48 }, payload={}, command={}, domain={}, ]. This operation will timeout if no session state has been received within 60 seconds.
tomcat-node-2    | FINE: Assuming clocks are synched: Replication for GET-ALL-localhost#/session-replication took=24 ms.
tomcat-node-2    | FINE: Manager [localhost#/session-replication]: Received SessionMessage of type=(SESSION-GET-ALL) from [org.apache.catalina.tribes.membership.MemberImpl[tcp://{172, 18, 0, 4}:4000,{172, 18, 0, 4},4000, alive=2536, securePort=-1, UDP Port=-1, id={90 -10 116 -67 -56 -126 67 -127 -107 -64 -50 14 64 -71 81 7 }, payload={}, command={}, domain={}, ]]
tomcat-node-2    | FINE: Manager [localhost#/session-replication]: start unloading sessions
tomcat-node-2    | FINE: writeObject() storing session [A97DD384040BD3557F9F17182868A45A.node2]
tomcat-node-2    | FINE: Manager [localhost#/session-replication]: unloading sessions complete
tomcat-node-2    | FINE: Manager [localhost#/session-replication] send all session data.
tomcat-node-1    | FINE: Assuming clocks are synched: Replication for SESSION-STATE-localhost#/session-replication took=13 ms.
tomcat-node-1    | FINE: Manager [localhost#/session-replication]: Received SessionMessage of type=(ALL-SESSION-DATA) from [org.apache.catalina.tribes.membership.MemberImpl[tcp://{172, 18, 0, 3}:4000,{172, 18, 0, 3},4000, alive=424292, securePort=-1, UDP Port=-1, id={87 34 -54 -91 81 34 69 -16 -90 34 -122 24 84 -84 -128 -48 }, payload={}, command={}, domain={}, ]]
tomcat-node-1    | FINE: Manager [localhost#/session-replication]: received session state data
tomcat-node-1    | FINE: readObject() loading session [A97DD384040BD3557F9F17182868A45A.node2]
tomcat-node-2    | FINE: Manager [localhost#/session-replication] send all session data transfered
```

#### Step 7
The same behaviour observer in **Step 4** occurs.

#### Step 8
Tomcat **A** uses the data that was retrieved from the Cluster on startup and is able to serve requests to Session 
**S1** with the data updated even it it was down when some requests were processed. 

## Additional Resources

* [Clustering/Session Replication HOW-TO - Configuration Example](https://tomcat.apache.org/tomcat-7.0-doc/cluster-howto.html#Configuration_Example)
* [Apache Module mod_proxy_balancer ](https://httpd.apache.org/docs/2.4/mod/mod_proxy_balancer.html)
