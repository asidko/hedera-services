/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.metrics;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.base.utility.ToStringBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.Objects;

/**
 * A {@code Counter} can be used to count events and similar things.
 * <p>
 * The value of a {@code Counter} is initially {@code 0} and can only be increased.
 */
public interface Counter extends Metric {

    /**
     * {@inheritDoc}
     */
    @Override
    default MetricType getMetricType() {
        return MetricType.COUNTER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default DataType getDataType() {
        return DataType.INT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default Long get(final ValueType valueType) {
        Objects.requireNonNull(valueType, "valueType");
        if (valueType == VALUE) {
            return get();
        }
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    /**
     * Return the current value of the {@code Counter}
     *
     * @return the current value
     */
    long get();

    /**
     * Add a value to the {@code Counter}.
     * <p>
     * The value of a {@code Counter} can only increase, thus only non-negative numbers can be added.
     *
     * @param value
     * 		the value that needs to be added
     * @throws IllegalArgumentException
     * 		if {@code value <= 0}
     */
    void add(final long value);

    /**
     * Increase the {@code Counter} by {@code 1}.
     */
    void increment();

    /**
     * Configuration of a {@link Counter}.
     */
    final class Config extends MetricConfig<Counter, Counter.Config> {

        /**
         * Constructor of {@code Counter.Config}
         *
         * @param category
         * 		the kind of metric (metrics are grouped or filtered by this)
         * @param name
         * 		a short name for the metric
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(@NonNull final String category, @NonNull final String name) {
            super(category, name, "%d");
        }

        private Config(
                @NonNull final String category,
                @NonNull final String name,
                @NonNull final String description,
                @NonNull final String unit) {
            super(category, name, description, unit, "%d");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Counter.Config withDescription(@NonNull final String description) {
            return new Counter.Config(getCategory(), getName(), description, getUnit());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Counter.Config withUnit(@NonNull final String unit) {
            return new Counter.Config(getCategory(), getName(), getDescription(), unit);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<Counter> getResultClass() {
            return Counter.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public Counter create(@NonNull final MetricsFactory factory) {
            return factory.createCounter(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this).appendSuper(super.toString()).toString();
        }
    }
}
