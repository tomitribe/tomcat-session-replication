/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.tomitribe.support;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;

import java.net.URI;

public class SessionServicePerf extends Assert {

    public static void main(String[] args) throws Exception {
        final SessionServicePerf perf = new SessionServicePerf("http://localhost");
        perf.callServer1();
        perf.callServer2();
    }

    private final CloseableHttpClient httpClient1;
    private final CloseableHttpClient httpClient2;
    private final CloseableHttpClient loadBalancerClient;
    private final URI server1;
    private final URI server2;
    private final URI loadBalancer;

    public SessionServicePerf(String webappUrl) {
        server1 = URI.create(webappUrl + ":8081");
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(30);
        httpClient1 = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        server2 = URI.create(webappUrl + ":8082");
        httpClient2 = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        loadBalancer = URI.create(webappUrl);
        loadBalancerClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();
    }

    public void callServer1() throws Exception {
        {
            final HttpGet get = new HttpGet(server1.resolve("/session-replication"));
            try (final CloseableHttpResponse response = httpClient1.execute(get)) {
                final String content = EntityUtils.toString(response.getEntity());
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertTrue(content.contains("session id"));
            }
        }

    }

    public void callServer2() throws Exception {
        {
            final HttpGet get = new HttpGet(server2.resolve("/session-replication"));
            try (final CloseableHttpResponse response = httpClient2.execute(get)) {
                final String content = EntityUtils.toString(response.getEntity());
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertTrue(content.contains("session id"));
            }
        }
    }

    public void callLoadBalancer() throws Exception {
        {
            final HttpGet get = new HttpGet(loadBalancer.resolve("/session-replication"));
            try (final CloseableHttpResponse response = loadBalancerClient.execute(get)) {
                final String content = EntityUtils.toString(response.getEntity());
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertTrue(content.contains("session id"));
            }
        }
    }
}
