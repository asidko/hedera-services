package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.proxy.RedirectGasCalculator;
import com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.BurnLogic;
import com.hedera.services.txns.token.CreateLogic;
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;

@Singleton
public class InfrastructureFactory {
	private final EntityIdSource ids;
	private final EncodingFacade encoder;
	private final DecodingFacade decoder;
	private final OptionValidator validator;
	private final RecordsHistorian recordsHistorian;
	private final SigImpactHistorian sigImpactHistorian;
	private final DissociationFactory dissociationFactory;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public InfrastructureFactory(
			final EntityIdSource ids,
			final EncodingFacade encoder,
			final DecodingFacade decoder,
			final OptionValidator validator,
			final RecordsHistorian recordsHistorian,
			final SigImpactHistorian sigImpactHistorian,
			final DissociationFactory dissociationFactory,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.ids = ids;
		this.encoder = encoder;
		this.decoder = decoder;
		this.validator = validator;
		this.recordsHistorian = recordsHistorian;
		this.dynamicProperties = dynamicProperties;
		this.sigImpactHistorian = sigImpactHistorian;
		this.dissociationFactory = dissociationFactory;
	}

	public SideEffectsTracker newSideEffects() {
		return new SideEffectsTracker();
	}

	public AccountStore newAccountStore(final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
		return new AccountStore(validator, accounts);
	}

	public TypedTokenStore newTokenStore(
			final AccountStore accountStore,
			final SideEffectsTracker sideEffects,
			final BackingStore<TokenID, MerkleToken> tokens,
			final BackingStore<NftId, MerkleUniqueToken> uniqueTokens,
			final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels
	) {
		return new TypedTokenStore(accountStore, tokens, uniqueTokens, tokenRels, sideEffects);
	}

	public HederaTokenStore newHederaTokenStore(
			final SideEffectsTracker sideEffects,
			final BackingStore<TokenID, MerkleToken> backingTokens,
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger
	) {
		return new HederaTokenStore(
				NOOP_ID_SOURCE, validator, sideEffects, dynamicProperties, tokenRelsLedger, nftsLedger, backingTokens);
	}

	public BurnLogic newBurnLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
		return new BurnLogic(validator, tokenStore, accountStore, dynamicProperties);
	}

	public MintLogic newMintLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
		return new MintLogic(validator, tokenStore, accountStore, dynamicProperties);
	}

	public AssociateLogic newAssociateLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
		return new AssociateLogic(tokenStore, accountStore, dynamicProperties);
	}

	public DissociateLogic newDissociateLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
		return new DissociateLogic(validator, tokenStore, accountStore, dissociationFactory);
	}

	public CreateLogic newTokenCreateLogic(
			final AccountStore accountStore,
			final TypedTokenStore tokenStore
	) {
		return new CreateLogic(
				accountStore, tokenStore, dynamicProperties, sigImpactHistorian, ids, validator);
	}

	public TransferLogic newTransferLogic(
			final HederaTokenStore tokenStore,
			final SideEffectsTracker sideEffects,
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger
	) {
		return new TransferLogic(
				accountsLedger,
				nftsLedger,
				tokenRelsLedger,
				tokenStore,
				sideEffects,
				dynamicProperties,
				validator,
				null,
				recordsHistorian);
	}

	public RedirectViewExecutor newRedirectExecutor(
			final Bytes input,
			final MessageFrame frame,
			final RedirectGasCalculator gasCalculator
	) {
		return new RedirectViewExecutor(input, frame, encoder, decoder, gasCalculator);
	}

	public ApproveAllowanceLogic newApproveAllowanceLogic(
			final AccountStore accountStore,
			final TypedTokenStore tokenStore
	) {
		return new ApproveAllowanceLogic(accountStore, tokenStore, dynamicProperties);
	}

	public DeleteAllowanceLogic newDeleteAllowanceLogic(
			final AccountStore accountStore,
			final TypedTokenStore tokenStore
	) {
		return new DeleteAllowanceLogic(accountStore, tokenStore);
	}

	public CreateChecks newCreateChecks() {
		return new CreateChecks(dynamicProperties, validator);
	}

	public ApproveAllowanceChecks newApproveAllowanceChecks() {
		return new ApproveAllowanceChecks(dynamicProperties, validator);
	}

	public DeleteAllowanceChecks newDeleteAllowanceChecks() {
		return new DeleteAllowanceChecks(dynamicProperties, validator);
	}
}