/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.internal;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of a {@link com.swirlds.common.system.Round} used by streaming classes.
 */
public class StreamedRound implements Round {

    private final List<EventImpl> events;
    private final long roundNumber;
    private final Instant consensusTimestamp;
    private final AddressBook consensusRoster;

    public StreamedRound(
            @NonNull final AddressBook consensusRoster, @NonNull final List<EventImpl> events, final long roundNumber) {
        this.events = events;
        this.roundNumber = roundNumber;
        events.forEach(EventImpl::consensusReached);
        consensusTimestamp = events.get(events.size() - 1).getConsensusTimestamp();
        this.consensusRoster = Objects.requireNonNull(consensusRoster);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Iterator<ConsensusEvent> iterator() {
        final Iterator<EventImpl> iterator = events.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ConsensusEvent next() {
                return iterator.next();
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRoundNum() {
        return roundNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEventCount() {
        return events.size();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public AddressBook getConsensusRoster() {
        return consensusRoster;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }
}
