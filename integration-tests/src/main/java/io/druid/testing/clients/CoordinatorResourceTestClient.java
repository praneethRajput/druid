/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.testing.clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.metamx.common.ISE;
import com.metamx.common.logger.Logger;
import com.metamx.http.client.HttpClient;
import com.metamx.http.client.RequestBuilder;
import com.metamx.http.client.response.StatusResponseHandler;
import com.metamx.http.client.response.StatusResponseHolder;
import io.druid.guice.annotations.Global;
import io.druid.testing.IntegrationTestingConfig;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.joda.time.Interval;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

public class CoordinatorResourceTestClient
{
  private final static Logger LOG = new Logger(CoordinatorResourceTestClient.class);
  private final ObjectMapper jsonMapper;
  private final HttpClient httpClient;
  private final String coordinator;
  private final StatusResponseHandler responseHandler;

  @Inject
  CoordinatorResourceTestClient(
      ObjectMapper jsonMapper,
      @Global HttpClient httpClient, IntegrationTestingConfig config
  )
  {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.coordinator = config.getCoordinatorHost();
    this.responseHandler = new StatusResponseHandler(Charsets.UTF_8);
  }

  private String getCoordinatorURL()
  {
    return String.format(
        "http://%s/druid/coordinator/v1/",
        coordinator
    );
  }

  private Map<String, Integer> getLoadStatus()
  {
    Map<String, Integer> status = null;
    try {
      StatusResponseHolder response = makeRequest(HttpMethod.GET, getCoordinatorURL() + "loadstatus?simple");

      status = jsonMapper.readValue(
          response.getContent(), new TypeReference<Map<String, Integer>>()
          {
          }
      );
    }
    catch (Exception e) {
      Throwables.propagate(e);
    }
    return status;
  }

  public boolean areSegmentsLoaded(String dataSource)
  {
    final Map<String, Integer> status = getLoadStatus();
    return (status.containsKey(dataSource) && status.get(dataSource) == 0);
  }

  public void unloadSegmentsForDataSource(String dataSource, Interval interval)
  {
    killDataSource(dataSource, false, interval);
  }

  public void deleteSegmentsDataSource(String dataSource, Interval interval)
  {
    killDataSource(dataSource, true, interval);
  }

  private void killDataSource(String dataSource, boolean kill, Interval interval)
  {
    try {
      makeRequest(
          HttpMethod.DELETE,
          String.format(
              "%sdatasources/%s?kill=%s&interval=%s",
              getCoordinatorURL(),
              dataSource, kill, URLEncoder.encode(interval.toString(), "UTF-8")
          )
      );
    }
    catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private StatusResponseHolder makeRequest(HttpMethod method, String url)
  {
    try {
      StatusResponseHolder response = new RequestBuilder(
          this.httpClient,
          method, new URL(url)
      )
          .go(responseHandler)
          .get();
      if (!response.getStatus().equals(HttpResponseStatus.OK)) {
        throw new ISE(
            "Error while making request to url[%s] status[%s] content[%s]",
            url,
            response.getStatus(),
            response.getContent()
        );
      }
      return response;
    }
    catch (Exception e) {
      LOG.error(e, "Exception while sending request");
      throw Throwables.propagate(e);
    }
  }
}
