package com.hedera.services.usage.util;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;

import javax.inject.Inject;

public class UtilOpsUsage {

	@Inject
	public UtilOpsUsage() {
		// Default constructor
	}

	public void randomGenerateUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final RandomGenerateMeta randomGenerateMeta,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);
		var baseSize = randomGenerateMeta.getMsgBytesUsed();
		accumulator.addBpt(baseSize);
	}
}