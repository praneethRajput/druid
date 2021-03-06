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

package io.druid.query.groupby;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.metamx.common.ISE;
import com.metamx.common.Pair;
import com.metamx.common.guava.Accumulator;
import io.druid.collections.StupidPool;
import io.druid.data.input.Row;
import io.druid.data.input.Rows;
import io.druid.granularity.QueryGranularity;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.dimension.DimensionSpec;
import io.druid.segment.incremental.IncrementalIndex;
import io.druid.segment.incremental.IndexSizeExceededException;
import io.druid.segment.incremental.OffheapIncrementalIndex;
import io.druid.segment.incremental.OnheapIncrementalIndex;

import java.nio.ByteBuffer;
import java.util.List;

public class GroupByQueryHelper
{
  public static <T> Pair<IncrementalIndex, Accumulator<IncrementalIndex, T>> createIndexAccumulatorPair(
      final GroupByQuery query,
      final GroupByQueryConfig config,
      StupidPool<ByteBuffer> bufferPool

  )
  {
    final QueryGranularity gran = query.getGranularity();
    final long timeStart = query.getIntervals().get(0).getStartMillis();

    // use gran.iterable instead of gran.truncate so that
    // AllGranularity returns timeStart instead of Long.MIN_VALUE
    final long granTimeStart = gran.iterable(timeStart, timeStart + 1).iterator().next();

    final List<AggregatorFactory> aggs = Lists.transform(
        query.getAggregatorSpecs(),
        new Function<AggregatorFactory, AggregatorFactory>()
        {
          @Override
          public AggregatorFactory apply(AggregatorFactory input)
          {
            return input.getCombiningFactory();
          }
        }
    );
    final List<String> dimensions = Lists.transform(
        query.getDimensions(),
        new Function<DimensionSpec, String>()
        {
          @Override
          public String apply(DimensionSpec input)
          {
            return input.getOutputName();
          }
        }
    );
    final IncrementalIndex index;
    if (query.getContextValue("useOffheap", false)) {
      index = new OffheapIncrementalIndex(
          // use granularity truncated min timestamp
          // since incoming truncated timestamps may precede timeStart
          granTimeStart,
          gran,
          aggs.toArray(new AggregatorFactory[aggs.size()]),
          bufferPool,
          false,
          Integer.MAX_VALUE
      );
    } else {
      index = new OnheapIncrementalIndex(
          // use granularity truncated min timestamp
          // since incoming truncated timestamps may precede timeStart
          granTimeStart,
          gran,
          aggs.toArray(new AggregatorFactory[aggs.size()]),
          false,
          config.getMaxResults()
      );
    }

    Accumulator<IncrementalIndex, T> accumulator = new Accumulator<IncrementalIndex, T>()
    {
      @Override
      public IncrementalIndex accumulate(IncrementalIndex accumulated, T in)
      {

        if (in instanceof Row) {
          try {
            accumulated.add(Rows.toCaseInsensitiveInputRow((Row) in, dimensions));
          } catch(IndexSizeExceededException e) {
            throw new ISE(e.getMessage());
          }
        } else {
          throw new ISE("Unable to accumulate something of type [%s]", in.getClass());
        }

        return accumulated;
      }
    };
    return new Pair<>(index, accumulator);
  }

  public static <T> Pair<List, Accumulator<List, T>> createBySegmentAccumulatorPair()
  {
    List init = Lists.newArrayList();
    Accumulator<List, T> accumulator = new Accumulator<List, T>()
    {
      @Override
      public List accumulate(List accumulated, T in)
      {
        accumulated.add(in);
        return accumulated;
      }
    };
    return new Pair<>(init, accumulator);
  }
}
