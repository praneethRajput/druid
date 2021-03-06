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

package io.druid.query.topn;

/**
 */
public class TopNAlgorithmSelector
{
  private final int cardinality;
  private final int numBytesPerRecord;

  private volatile boolean hasDimExtractionFn;
  private volatile boolean aggregateAllMetrics;
  private volatile boolean aggregateTopNMetricFirst;

  public TopNAlgorithmSelector(int cardinality, int numBytesPerRecord)
  {
    this.cardinality = cardinality;
    this.numBytesPerRecord = numBytesPerRecord;
  }

  public void setHasDimExtractionFn(boolean hasDimExtractionFn)
  {
    this.hasDimExtractionFn = hasDimExtractionFn;
  }

  public void setAggregateAllMetrics(boolean aggregateAllMetrics)
  {
    this.aggregateAllMetrics = aggregateAllMetrics;
  }

  public void setAggregateTopNMetricFirst(boolean aggregateTopNMetricFirst)
  {
    // These are just heuristics based on an analysis of where an inflection point may lie to switch
    // between different algorithms
    if (cardinality > 400000 && numBytesPerRecord > 100) {
      this.aggregateTopNMetricFirst = aggregateTopNMetricFirst;
    }
  }

  public boolean isHasDimExtractionFn()
  {
    return hasDimExtractionFn;
  }

  public boolean isAggregateAllMetrics()
  {
    return aggregateAllMetrics;
  }

  public boolean isAggregateTopNMetricFirst()
  {
    return aggregateTopNMetricFirst;
  }
}
