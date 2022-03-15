package com.hedera.services.contracts.sources;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.store.contracts.WorldLedgers;
import org.hyperledger.besu.datatypes.Address;

public interface EvmSigsVerifier {
	/**
	 * Determines if the target account has an active key given the cryptographic signatures from the
	 * {@link com.hederahashgraph.api.proto.java.SignatureMap} that could be verified asynchronously; plus
	 * the given recipient and contract of the current {@link org.hyperledger.besu.evm.frame.MessageFrame}.
	 *
	 * If the account's key includes a {@code contractID} key matching the contract address, or a
	 * {@code delegatableContractId} key matching the recipient address, then those keys must be treated
	 * as active for the purposes of this test.
	 *
	 * Does <b>not</b> perform any synchronous signature verification.
	 *

	 * @param isDelegateCall
	 * 		a flag showing if the message represented by the active frame is invoked via {@code delegatecall}
	 * @param account
	 *		the address of the account to test for key activation
	 * @param activeContract
	 * 		the address of the contract that is deemed active
	 * @param worldLedgers
	 * 		the worldLedgers representing current state
	 * @return whether the target account's key has an active signature
	 */
	boolean hasActiveKey(
			boolean isDelegateCall, Address account, Address activeContract,
			WorldLedgers worldLedgers);

	/**
	 * Determines if the target account <b>either</b> has no receiver sig requirement; or an active key given
	 * the cryptographic signatures from the {@link com.hederahashgraph.api.proto.java.SignatureMap}, plus the
	 * given recipient and contract of the current {@link org.hyperledger.besu.evm.frame.MessageFrame}.
	 *
	 * If the account's key includes a {@code contractID} key matching the contract address, or a
	 * {@code delegatableContractId} key matching the recipient address, then those keys must be treated
	 * as active for the purposes of this test.
	 *
	 * Does <b>not</b> perform any synchronous signature verification.
	 *
	 * @param isDelegateCall
	 * 		a flag showing if the message represented by the active frame is invoked via {@code delegatecall}
	 * @param target
	 * 		the account to test for receiver sig requirement and key activation
	 * @param activeContract
	 * 		the address of the contract that is deemed active
	 * @param worldLedgers
	 * 		the worldLedgers representing current state
	 * @return false if the account requires a receiver sig but has no active key; true otherwise
	 */
	boolean hasActiveKeyOrNoReceiverSigReq(
			boolean isDelegateCall, Address target, Address activeContract,
			WorldLedgers worldLedgers);

	/**
	 * Determines if the target token has an active supply key given the cryptographic signatures from the
	 * {@link com.hederahashgraph.api.proto.java.SignatureMap} that could be verified asynchronously; plus
	 * the given recipient and contract of the current {@link org.hyperledger.besu.evm.frame.MessageFrame}.
	 *
	 * If the supply key includes a {@code contractID} key matching the contract address, or a
	 * {@code delegatableContractId} key matching the recipient address, then those keys must be treated
	 * as active for the purposes of this test.
	 *
	 * Does <b>not</b> perform any synchronous signature verification.
	 *
	 * @param isDelegateCall
	 * 		a flag showing if the message represented by the active frame is invoked via {@code delegatecall}
	 * @param token
	 * 		the address of the token to test for supply key activation
	 * @param activeContract
	 * 		the address of the contract that should be signed in the key
	 * @param worldLedgers
	 * 		the worldLedgers representing current state
	 * @return whether the target account's key has an active signature
	 */
	boolean hasActiveSupplyKey(
			boolean isDelegateCall, Address token, Address activeContract, WorldLedgers worldLedgers);
}