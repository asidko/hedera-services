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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Wiring for the {@link OrphanBuffer}.
 *
 * @param eventInput                       the input wire for unordered events
 * @param minimumGenerationNonAncientInput the input wire for the minimum generation non-ancient
 * @param pauseInput                       the input wire for pausing the buffer
 * @param eventOutput                      the output wire for topologically ordered events
 * @param flushRunnable                    the runnable to flush the buffer
 */
public record OrphanBufferWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull InputWire<Long> minimumGenerationNonAncientInput,
        @NonNull InputWire<Boolean> pauseInput,
        @NonNull OutputWire<GossipEvent> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this buffer
     * @return the new wiring instance
     */
    public static OrphanBufferWiring create(@NonNull final TaskScheduler<List<GossipEvent>> taskScheduler) {
        return new OrphanBufferWiring(
                taskScheduler.buildInputWire("unordered events"),
                taskScheduler.buildInputWire("minimum generation non ancient"),
                taskScheduler.buildInputWire("pause"),
                taskScheduler.getOutputWire().buildSplitter(),
                taskScheduler::flush);
    }

    /**
     * Bind an orphan buffer to this wiring.
     *
     * @param orphanBuffer the orphan buffer to bind
     */
    public void bind(@NonNull final OrphanBuffer orphanBuffer) {
        ((BindableInputWire<GossipEvent, List<GossipEvent>>) eventInput).bind(orphanBuffer::handleEvent);
        ((BindableInputWire<Long, List<GossipEvent>>) minimumGenerationNonAncientInput)
                .bind(orphanBuffer::setMinimumGenerationNonAncient);
        ((BindableInputWire<Boolean, List<GossipEvent>>) pauseInput).bind(orphanBuffer::setPaused);
    }
}
