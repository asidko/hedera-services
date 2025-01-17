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

package com.swirlds.common.wiring.model.internal;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A vertex in a wiring model.
 */
public class ModelVertex implements Iterable<ModelEdge>, Comparable<ModelVertex> {

    /**
     * The name of the vertex.
     */
    private final String name;

    /**
     * When tasks are inserted into this vertex, is this component capable of applying back pressure?
     */
    private final boolean insertionIsBlocking;

    /**
     * The task scheduler type that corresponds to this vertex.
     */
    private final TaskSchedulerType type;

    /**
     * The outgoing edges of this vertex.
     */
    private final List<ModelEdge> outgoingEdges = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param name                the name of the vertex
     * @param type                the type of task scheduler that corresponds to this vertex
     * @param insertionIsBlocking true if the insertion of this vertex may block until capacity is available
     */
    public ModelVertex(
            @NonNull final String name, @NonNull final TaskSchedulerType type, final boolean insertionIsBlocking) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.insertionIsBlocking = insertionIsBlocking;
    }

    /**
     * Get the name of the vertex.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the type of task scheduler that corresponds to this vertex, or null if this vertex does not correspond to a
     * task scheduler.
     *
     * @return the type of task scheduler that corresponds to this vertex, or null if this vertex does not correspond to
     * a task scheduler
     */
    @NonNull
    public TaskSchedulerType getType() {
        return type;
    }

    /**
     * Get whether the insertion of this vertex may block until capacity is available.
     *
     * @return true if the insertion of this vertex may block until capacity is available
     */
    public boolean isInsertionIsBlocking() {
        return insertionIsBlocking;
    }

    /**
     * Add an outgoing edge to this vertex.
     *
     * @param vertex the edge to connect to
     */
    public void connectToEdge(@NonNull final ModelEdge vertex) {
        outgoingEdges.add(Objects.requireNonNull(vertex));
    }

    /**
     * Get an iterator that walks over the outgoing edges of this vertex.
     *
     * @return an iterator that walks over the outgoing edges of this vertex
     */
    @Override
    @NonNull
    public Iterator<ModelEdge> iterator() {
        return outgoingEdges.iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof final ModelVertex that) {
            return name.equals(that.name);
        }
        return false;
    }

    /**
     * Makes the vertex nicer to look at in a debugger.
     */
    @Override
    public String toString() {
        if (insertionIsBlocking) {
            return "[" + name + "]";
        } else {
            return "(" + name + ")";
        }
    }

    /**
     * Sorts vertices by alphabetical order.
     */
    @Override
    public int compareTo(@NonNull final ModelVertex that) {
        return name.compareTo(that.name);
    }
}
