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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.UNKNOWN_FAILURE;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Streams;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class PreHandleResultTest implements Scenarios {

    /**
     * Tests to verify the creation of the object. Simple null checks, and verifying that the different static
     * construction methods all creation proper objects.
     */
    @Nested
    @DisplayName("Testing Creation of PreHandleResult")
    @ExtendWith(MockitoExtension.class)
    final class CreationTests {
        /** The {@link PreHandleResult#status()} must not be null. */
        @Test
        @DisplayName("The status must not be null")
        @SuppressWarnings("ConstantConditions")
        void statusMustNotBeNull(
                @Mock AccountID payer, @Mock TransactionInfo txInfo, @Mock PreHandleResult innerResult) {
            final Map<Key, SignatureVerificationFuture> verificationResults = Map.of();
            assertThatThrownBy(() ->
                            new PreHandleResult(payer, Key.DEFAULT, null, OK, txInfo, verificationResults, innerResult))
                    .isInstanceOf(NullPointerException.class);
        }

        /** The {@link PreHandleResult#responseCode()} must not be null. */
        @Test
        @DisplayName("The response code must not be null")
        @SuppressWarnings("ConstantConditions")
        void responseCodeMustNotBeNull(
                @Mock AccountID payer, @Mock TransactionInfo txInfo, @Mock PreHandleResult innerResult) {
            final Map<Key, SignatureVerificationFuture> verificationResults = Map.of();
            assertThatThrownBy(() -> new PreHandleResult(
                            payer, Key.DEFAULT, SO_FAR_SO_GOOD, null, txInfo, verificationResults, innerResult))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Unknown failures set the status and response code and everything else is null")
        void unknownFailure() {
            final var result = PreHandleResult.unknownFailure();

            assertThat(result.status()).isEqualTo(UNKNOWN_FAILURE);
            assertThat(result.responseCode()).isEqualTo(UNKNOWN);
            assertThat(result.innerResult()).isNull();
            assertThat(result.payer()).isNull();
            assertThat(result.txInfo()).isNull();
            assertThat(result.verificationResults()).isNull();
            assertThat(result.verificationFor(ERIN.account().alias()))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);

            assertThat(result.verificationFor(Key.DEFAULT))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        @Test
        @DisplayName(
                "Node Diligence Failures only set the status and response code and the payer to be the node and the tx info")
        void nodeDiligenceFailure(@Mock TransactionInfo txInfo) {
            final var nodeAccountId = AccountID.newBuilder().accountNum(3).build();
            final var status = INVALID_PAYER_ACCOUNT_ID;
            final var result = PreHandleResult.nodeDueDiligenceFailure(nodeAccountId, status, txInfo);

            assertThat(result.status()).isEqualTo(NODE_DUE_DILIGENCE_FAILURE);
            assertThat(result.responseCode()).isEqualTo(status);
            assertThat(result.innerResult()).isNull();
            assertThat(result.payer()).isEqualTo(nodeAccountId);
            assertThat(result.txInfo()).isSameAs(txInfo);
            assertThat(result.verificationResults()).isNull();
            assertThat(result.verificationFor(ERIN.account().alias()))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);

            assertThat(result.verificationFor(Key.DEFAULT))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("Pre-Handle Failures set the payer, status, responseCode, and txInfo")
        void preHandleFailure(@Mock TransactionInfo txInfo) {
            final var payer = AccountID.newBuilder().accountNum(1001).build();
            final var responseCode = INVALID_PAYER_ACCOUNT_ID;
            final var result = PreHandleResult.preHandleFailure(payer, null, responseCode, txInfo, null);

            assertThat(result.status()).isEqualTo(PRE_HANDLE_FAILURE);
            assertThat(result.responseCode()).isEqualTo(responseCode);
            assertThat(result.innerResult()).isNull();
            assertThat(result.payer()).isEqualTo(payer);
            assertThat(result.txInfo()).isSameAs(txInfo);
            assertThat(result.verificationResults()).isNull();
            assertThat(result.verificationFor(ERIN.account().alias()))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);

            assertThat(result.verificationFor(Key.DEFAULT))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }
    }

    /**
     * Tests to verify that finding a {@link SignatureVerification} for cryptographic keys (ED25519, ECDSA_SECP256K1)
     * work as expected. No key lists or threshold keys involved.
     */
    @Nested
    @DisplayName("Finding SignatureVerification With Cryptographic Keys")
    @ExtendWith(MockitoExtension.class)
    final class FindingSignatureVerificationWithCryptoKeyTests {
        @Test
        @DisplayName("Null key or alias throws exception")
        @SuppressWarnings("DataFlowIssue")
        void nullKeyThrowsException() {
            final var result = PreHandleResult.unknownFailure();
            assertThatThrownBy(() -> result.verificationFor((Key) null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> result.verificationFor((Bytes) null)).isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("If there are no verification results, then the result is failed")
        void noVerificationResults(@NonNull final Key key) {
            final var result = PreHandleResult.unknownFailure();
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("If the key is a cryptographic key in the results then it is returned")
        void cryptoKeyIsPresent() {
            final var aliceKey = ALICE.keyInfo().publicKey(); // ECDSA
            final var aliceFuture = mock(SignatureVerificationFuture.class);
            final var bobKey = BOB.keyInfo().publicKey(); // ED25519
            final var bobFuture = mock(SignatureVerificationFuture.class);
            final var verificationResults = Map.of(aliceKey, aliceFuture, bobKey, bobFuture);
            final var result = preHandle(verificationResults);

            assertThat(result.verificationFor(aliceKey)).isSameAs(aliceFuture);
            assertThat(result.verificationFor(bobKey)).isSameAs(bobFuture);
        }

        @Test
        @DisplayName("If the key is a cryptographic key not in the results then null returned")
        void cryptoKeyIsMissing() {
            final var aliceKey = ALICE.keyInfo().publicKey(); // ECDSA
            final var aliceFuture = mock(SignatureVerificationFuture.class);
            final var bobKey = BOB.keyInfo().publicKey(); // ED25519
            final var bobFuture = mock(SignatureVerificationFuture.class);
            final var verificationResults = Map.of(aliceKey, aliceFuture, bobKey, bobFuture);
            final var result = preHandle(verificationResults);

            // ERIN is another ECDSA key, but one that is not in the verification results
            assertThat(result.verificationFor(ERIN.keyInfo().publicKey()))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        /** A provider that supplies basic cryptographic keys */
        static Stream<Arguments> provideCompoundKeys() {
            // FUTURE: Add RSA keys to this list
            return Stream.of(
                    Arguments.of(named("ED25519", FAKE_ED25519_KEY_INFOS[0].publicKey())),
                    Arguments.of(named("ECDSA_SECP256K1", FAKE_ECDSA_KEY_INFOS[0].publicKey())));
        }
    }

    /**
     * Tests to verify that finding a {@link SignatureVerification} for compound keys (threshold keys, key lists) that
     * also have duplicated keys. The point of these tests is really to verify that duplicate keys are counted multiple
     * times as expected when meeting threshold requirements.
     *
     * <p>We try testing all the boundary conditions:
     *      <ul>Just enough signatures and all are valid</ul>
     *      <ul>More than enough signatures but only a sufficient number are valid</ul>
     *      <ul>More than enough signatures and more than enough are valid</ul>
     *      <ul>More than enough signatures but not enough are valid</ul>
     *      <ul>Not enough signatures but all are valid</ul>
     * </ul>
     *
     * <p>And for those testing "more than needed" and "less than needed", we try to get right on the boundary condition
     * as well as all the other permutations.
     */
    @Nested
    @DisplayName("Finding SignatureVerification With Complex Keys with Duplicates")
    @ExtendWith(MockitoExtension.class)
    final class FindingSignatureVerificationWithDuplicateKeysTests {
        // Used once in the key list
        private static final Key ECDSA_X1 = FAKE_ECDSA_KEY_INFOS[1].publicKey();
        // Used twice in the key list
        private static final Key ECDSA_X2 = FAKE_ECDSA_KEY_INFOS[2].publicKey();
        // Used once in the key list
        private static final Key ED25519_X1 = FAKE_ED25519_KEY_INFOS[1].publicKey();
        // Used twice in the key list
        private static final Key ED25519_X2 = FAKE_ED25519_KEY_INFOS[2].publicKey();

        private Map<Key, SignatureVerificationFuture> verificationResults(Map<Key, Boolean> keysAndPassFail) {
            final var results = new HashMap<Key, SignatureVerificationFuture>();
            for (final var entry : keysAndPassFail.entrySet()) {
                results.put(
                        entry.getKey(),
                        new FakeSignatureVerificationFuture(
                                new SignatureVerificationImpl(entry.getKey(), null, entry.getValue())));
            }
            return results;
        }

        @Test
        @DisplayName("All signatures are valid for the KeyList")
        void allValidInKeyList() {
            // Given a KeyList with 6 different keys with 2 duplicates (4 unique keys) and
            // verification results for ALL 4 different keys that are PASSING
            final var keyList = KeyList.newBuilder()
                    .keys(ECDSA_X2, ECDSA_X2, ECDSA_X1, ED25519_X2, ED25519_X2, ED25519_X1)
                    .build();
            var key = Key.newBuilder().keyList(keyList).build();
            var verificationResults = verificationResults(Map.of(
                    ECDSA_X1, true,
                    ECDSA_X2, true,
                    ED25519_X1, true,
                    ED25519_X2, true));
            // When we pre handle
            var result = preHandle(verificationResults);
            // Then we find the verification results are passing because we have all keys signed
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        /**
         * If there are just enough signatures to meet the threshold and all are valid signatures, then the overall
         * verification will pass.
         */
        @ParameterizedTest
        @MethodSource("provideJustEnoughSignaturesAndAllAreValid")
        @DisplayName("Just enough signatures and all are valid")
        void justEnoughAndAllAreValid(@NonNull final Map<Key, Boolean> keysAndPassFail) {
            // Given a ThresholdList with a threshold of 3 and 6 different keys with 2 duplicates (4 unique keys) and
            // verification results for only 2 keys (1 that is a duplicate, one that is not), so that the threshold is
            // met
            final var keyList = KeyList.newBuilder()
                    .keys(ECDSA_X1, ECDSA_X2, ECDSA_X2, ED25519_X1, ED25519_X2, ED25519_X2)
                    .build();
            final var thresholdKey =
                    ThresholdKey.newBuilder().threshold(3).keys(keyList).build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            final var verificationResults = verificationResults(keysAndPassFail);
            // When we pre handle
            final var result = preHandle(verificationResults);
            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        static Stream<Arguments> provideJustEnoughSignaturesAndAllAreValid() {
            return Stream.of(
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X2=pass, ED25519_X1=pass",
                            Map.of(
                                    ECDSA_X2, true,
                                    ED25519_X1, true))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ED25519_X1, true,
                                    ED25519_X2, true))));
        }

        /**
         * If there are more than enough signatures, but only *just barely* enough signatures are valid that the
         * threshold is met, then the verification will still pass.
         */
        @ParameterizedTest
        @MethodSource("provideMoreThanEnoughAndJustEnoughValid")
        @DisplayName("More than enough signatures but only a sufficient number are valid")
        void moreThanEnoughAndJustEnoughValid(@NonNull final Map<Key, Boolean> keysAndPassFail) {
            // Given a ThresholdList with a threshold of 3 and 6 different keys with 2 duplicates (4 unique keys) and
            // verification results for 3 keys (1 that is a duplicate, two that are not), but only 2 of the three are
            // passing (where one of them is the duplicate), so that the threshold is met
            final var keyList = KeyList.newBuilder()
                    .keys(ECDSA_X1, ECDSA_X2, ECDSA_X2, ED25519_X1, ED25519_X2, ED25519_X2)
                    .build();
            final var thresholdKey =
                    ThresholdKey.newBuilder().threshold(3).keys(keyList).build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            final var verificationResults = verificationResults(keysAndPassFail);
            // When we pre handle
            final var result = preHandle(verificationResults);
            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        static Stream<Arguments> provideMoreThanEnoughAndJustEnoughValid() {
            return Stream.of(
                    // Every key answers, but just enough are valid to pass
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X1=fail, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X1, false,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=fail, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, false,
                                    ED25519_X1, true,
                                    ED25519_X2, true))),
                    // Some keys don't answer, but just enough are valid to pass
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X1=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X1, false))),
                    Arguments.of(named(
                            "ECDSA_X2=pass, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X2, true,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ED25519_X1, true,
                                    ED25519_X2, true))),
                    // Some other keys don't answer, but just enough are valid to pass
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X1=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X1, true))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X2=fail, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X2, false,
                                    ED25519_X1, true,
                                    ED25519_X2, true))));
        }

        /**
         * More than enough signatures were provided, and more than were needed actually passed. The overall
         * verification therefore also passes.
         */
        @ParameterizedTest
        @MethodSource("provideMoreThanEnoughAndMoreThanNeededAreValid")
        @DisplayName("More than enough signatures and more than enough are valid")
        void moreThanEnoughAndMoreThanNeededAreValid(@NonNull final Map<Key, Boolean> keysAndPassFail) {
            // Given a ThresholdList with a threshold of 3 and 6 different keys with 2 duplicates (4 unique keys) and
            // verification results for 3 keys (1 that is a duplicate, two that are not), and all three are passing,
            // so that the threshold is met, plus more!
            final var keyList = KeyList.newBuilder()
                    .keys(ECDSA_X1, ECDSA_X2, ECDSA_X2, ED25519_X1, ED25519_X2, ED25519_X2)
                    .build();
            final var thresholdKey =
                    ThresholdKey.newBuilder().threshold(3).keys(keyList).build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            final var verificationResults = verificationResults(keysAndPassFail);
            // When we pre handle
            final var result = preHandle(verificationResults);
            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        static Stream<Arguments> provideMoreThanEnoughAndMoreThanNeededAreValid() {
            return Stream.of(
                    // Every key answers, and all are valid
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X1, true,
                                    ED25519_X2, true))),

                    // Every key answers, one or more is invalid, but still more than we need
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X1, true,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X1, true,
                                    ED25519_X2, true))),

                    // Some keys don't answer, but all are valid (more than enough)
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X1=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X1, true))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, true,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X2=pass, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X2, true,
                                    ED25519_X1, true,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X2=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X2, true,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ED25519_X1=pass, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ED25519_X1, true,
                                    ED25519_X2, true))));
        }

        /**
         * In this test there are more than enough keys in the signature ot meet the threshold, if they all passed.
         * But it turns out, that enough of them did NOT pass, that the threshold is not met, and the overall
         * verification is therefore failed.
         */
        @ParameterizedTest
        @MethodSource("provideMoreThanEnoughButNotEnoughValid")
        @DisplayName("More than enough signatures but not enough are valid")
        void moreThanEnoughButNotEnoughValid(@NonNull final Map<Key, Boolean> keysAndPassFail) {
            // Given a ThresholdList with a threshold of 3 and 6 different keys with 2 duplicates (4 unique keys) and
            // verification results for 3 keys (1 that is a duplicate, two that are not), and only the two non-duplicate
            // keys are passing, so the threshold is NOT met.
            final var keyList = KeyList.newBuilder()
                    .keys(ECDSA_X1, ECDSA_X2, ECDSA_X2, ED25519_X1, ED25519_X2, ED25519_X2)
                    .build();
            final var thresholdKey =
                    ThresholdKey.newBuilder().threshold(3).keys(keyList).build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            final var verificationResults = verificationResults(keysAndPassFail);
            // When we pre handle
            final var result = preHandle(verificationResults);
            // Then we find the verification results are NOT passing because we have NOT met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        static Stream<Arguments> provideMoreThanEnoughButNotEnoughValid() {
            return Stream.of(
                    // Every key answers, but not enough are valid
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X1=fail, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X1, false,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X1=fail, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X1, false,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=fail, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, false,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=fail, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, false,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),

                    // Some keys don't answer, and those that do don't cross the threshold
                    Arguments.of(named(
                            "ECDSA_X2=pass, ED25519_X1=fail, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X2, true,
                                    ED25519_X1, false,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ED25519_X1=fail, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ED25519_X1, false,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, false,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X1=fail",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X1, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X1=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X1, false))),
                    Arguments.of(named(
                            "ECDSA_X2=fail, ED25519_X1=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X2, false,
                                    ED25519_X1, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X2=fail, ED25519_X1=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X2, false,
                                    ED25519_X1, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X1=pass",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X1, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=pass, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, true,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=pass, ECDSA_X2=fail, ED25519_X2=fail",
                            Map.of(
                                    ECDSA_X1, true,
                                    ECDSA_X2, false,
                                    ED25519_X2, false))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=fail, ED25519_X2=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, false,
                                    ED25519_X2, true))),
                    Arguments.of(named(
                            "ECDSA_X1=fail, ECDSA_X2=fail, ED25519_X1=pass",
                            Map.of(
                                    ECDSA_X1, false,
                                    ECDSA_X2, false,
                                    ED25519_X1, true))));
        }

        /**
         * In this test, every signature is valid, but there just are not enough signatures to meet the threshold,
         * so the overall verification must fail.
         */
        @ParameterizedTest
        @MethodSource("provideNotEnoughSignatures")
        @DisplayName("Not enough signatures but all are valid")
        void notEnoughSignatures(@NonNull final Map<Key, Boolean> keysAndPassFail) {
            // Given a ThresholdList with a threshold of 3 and 6 different keys with 2 duplicates (4 unique keys) and
            // there are only verification results for 1 key, which isn't enough to meet the threshold.
            final var keyList = KeyList.newBuilder()
                    .keys(ECDSA_X1, ECDSA_X2, ECDSA_X2, ED25519_X1, ED25519_X2, ED25519_X2)
                    .build();
            final var thresholdKey =
                    ThresholdKey.newBuilder().threshold(3).keys(keyList).build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            final var verificationResults = verificationResults(keysAndPassFail);
            // When we pre handle
            final var result = preHandle(verificationResults);
            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        static Stream<Arguments> provideNotEnoughSignatures() {
            return Stream.of(
                    // Every key answers, but not enough are valid
                    Arguments.of(named("ECDSA_X1=pass", Map.of(ECDSA_X1, true))), // 1 of 3
                    Arguments.of(named("ECDSA_X2=pass", Map.of(ECDSA_X2, true))), // 2 of 3
                    Arguments.of(named("ED25519_X1=pass", Map.of(ED25519_X1, true))), // 1 of 3
                    Arguments.of(named("ED25519_X2=pass", Map.of(ED25519_X2, true))), // 2 of 3
                    Arguments.of(named(
                            "ECDSA_X1=pass, ED25519_X1=pass", Map.of(ECDSA_X1, true, ED25519_X1, true)))); // 2 of 3
        }
    }

    /**
     * Various targeted tests for {@link ThresholdKey} and {@link KeyList} lookup.
     */
    @Nested
    @DisplayName("Finding SignatureVerification With Threshold and KeyList Keys")
    @ExtendWith(MockitoExtension.class)
    final class FindingSignatureVerificationWithCompoundKeyTests {

        // A ThresholdKey with a threshold greater than max keys acts like a KeyList

        @Test
        @DisplayName("An empty KeyList never validates")
        void emptyKeyList() {
            // Given a KeyList with no keys
            final var keyList = KeyList.newBuilder().build();
            final var key = Key.newBuilder().keyList(keyList).build();
            // When we pre handle
            final var result = preHandle(emptyMap());
            // Then we find the verification results will fail
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0})
        @DisplayName("A threshold of less than 1 is clamped to 1")
        void thresholdLessThanOne(final int threshold) {
            // Given a ThresholdKey with a threshold less than 1
            final var thresholdKey = ThresholdKey.newBuilder()
                    .threshold(threshold)
                    .keys(KeyList.newBuilder()
                            .keys(FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ED25519_KEY_INFOS[0].publicKey()))
                    .build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();

            // First, verify that if there are NO valid verification results the threshold verification fails
            Map<Key, SignatureVerificationFuture> verificationResults =
                    Map.of(FAKE_ECDSA_KEY_INFOS[1].publicKey(), goodFuture(FAKE_ECDSA_KEY_INFOS[1].publicKey()));
            var result = preHandle(verificationResults);
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);

            // Now verify that if we verify with one valid verification result, the threshold verification passes
            verificationResults =
                    Map.of(FAKE_ECDSA_KEY_INFOS[0].publicKey(), goodFuture(FAKE_ECDSA_KEY_INFOS[0].publicKey()));
            // When we pre handle
            result = preHandle(verificationResults);
            // Then we find the verification results will pass if we have at least 1 valid signature
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("A threshold greater than the number of keys is clamped to the number of keys")
        void thresholdGreaterThanNumKeys() {
            // Given a ThresholdKey with a threshold greater than the number of keys
            final var thresholdKey = ThresholdKey.newBuilder()
                    .threshold(3)
                    .keys(KeyList.newBuilder()
                            .keys(FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ED25519_KEY_INFOS[0].publicKey()))
                    .build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            final Map<Key, SignatureVerificationFuture> verificationResults = Map.of(
                    FAKE_ECDSA_KEY_INFOS[0].publicKey(), goodFuture(FAKE_ECDSA_KEY_INFOS[0].publicKey()),
                    FAKE_ED25519_KEY_INFOS[0].publicKey(), goodFuture(FAKE_ED25519_KEY_INFOS[0].publicKey()));

            // When we pre handle
            var result = preHandle(verificationResults);

            // Then we find the verification results will pass
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        /**
         * If there are no verification results at all, then no matter what key we throw at it, we should get back
         * a failed verification.
         */
        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("A ThresholdKey or KeyList with no verification results returns a failed SignatureVerification")
        void keyWithNoVerificationResults(@NonNull final Key key) {
            final var result = preHandle(emptyMap());
            final var future = result.verificationFor(key);
            assertThat(future).isNotNull();
            assertThat(future.isDone()).isTrue();
            assertThat(future)
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        /**
         * If there are just enough signatures to meet the threshold and all are valid signatures, then the overall
         * verification will pass.
         */
        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("Just enough signatures and all are valid")
        void justEnoughAndAllAreValid(@NonNull final Key key) {
            // Given a barely sufficient number of signatures, all of which are valid
            final var verificationResults = allVerifications(key);
            removeVerificationsFrom(key, verificationResults, false);

            // When we pre handle
            final var result = preHandle(verificationResults);

            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        /**
         * If there are more than enough signatures, but only *just barely* enough signatures are valid that the
         * threshold is met, then the verification will still pass.
         */
        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("More than enough signatures but only a sufficient number are valid")
        void moreThanEnoughAndJustEnoughValid(@NonNull final Key key) {
            // Given more than enough validations but just barely enough of them are valid
            final var verificationResults = allVerifications(key);
            failVerificationsIn(key, verificationResults, false);

            // When we pre handle
            final var result = preHandle(verificationResults);

            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        /**
         * More than enough signatures were provided, and more than were needed actually passed. The overall
         * verification therefore also passes.
         */
        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("More than enough signatures and more than enough are valid")
        void moreThanEnoughAndMoreThanNeededAreValid(@NonNull final Key key) {
            // Given more than enough validations but just barely enough of them are valid
            final Map<Key, SignatureVerificationFuture> verificationResults = allVerifications(key);

            // When we pre handle
            final var result = preHandle(verificationResults);

            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(true);
        }

        /**
         * In this test there are more than enough keys in the signature ot meet the threshold, if they all passed.
         * But it turns out, that enough of them did NOT pass, that the threshold is not met, and the overall
         * verification is therefore failed.
         */
        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("More than enough signatures but not enough are valid")
        void moreThanEnoughButNotEnoughValid(@NonNull final Key key) {
            // Given more than enough validations but not enough of them are valid
            final var verificationResults = allVerifications(key);
            failVerificationsIn(key, verificationResults, true);

            // When we pre handle
            final var result = preHandle(verificationResults);

            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        /**
         * In this test, every signature is valid, but there just are not enough signatures to meet the threshold,
         * so the overall verification must fail.
         */
        @ParameterizedTest
        @MethodSource("provideCompoundKeys")
        @DisplayName("Not enough signatures but all are valid")
        void notEnoughSignatures(@NonNull final Key key) {
            // Given not enough signatures
            final var verificationResults = allVerifications(key);
            removeVerificationsFrom(key, verificationResults, true);

            // When we pre handle
            final var result = preHandle(verificationResults);

            // Then we find the verification results are passing because we have met the minimum threshold
            assertThat(result.verificationFor(key))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        /** A provider that supplies a mixture of KeyLists and ThresholdKeys, all of which are good keys. */
        static Stream<Arguments> provideCompoundKeys() {
            // FUTURE: Add RSA keys to this list
            return Streams.concat(provideKeyLists(), provideThresholdKeys());
        }

        /**
         * Provides a comprehensive set of KeyLists, including with nesting of KeyLists and ThresholdKeys. At most, we
         * return a KeyList with a depth of 3 and with up to 4 elements, one for each type of key that we support. This
         * provider does not create duplicates, those scenarios are tested separately.
         */
        static Stream<Arguments> provideKeyLists() {
            return keyListPermutations().entrySet().stream()
                    .map(entry -> of(named(
                            "KeyList(" + entry.getKey() + ")",
                            Key.newBuilder().keyList(entry.getValue()).build())));
        }

        /**
         * A provider specifically for all permutations of a valid threshold key, including those with duplicate keys
         * and nesting.
         */
        static Stream<Arguments> provideThresholdKeys() {
            return keyListPermutations().entrySet().stream().map(entry -> {
                final var keys = entry.getValue().keysOrThrow();
                final var threshold = Math.max(1, keys.size() / 2);
                final var thresholdKey = Key.newBuilder()
                        .thresholdKey(ThresholdKey.newBuilder()
                                .threshold(threshold)
                                .keys(KeyList.newBuilder().keys(keys)))
                        .build();
                return of(named("ThresholdKey(" + threshold + ", " + entry.getKey() + ")", thresholdKey));
            });
        }

        /** Generates the set of test permutations shared between KeyLists and ThresholdKeys. */
        private static Map<String, KeyList> keyListPermutations() {
            final var map = new LinkedHashMap<String, KeyList>();
            // FUTURE: Add RSA keys to this list
            final List<Function<Integer, Map.Entry<String, Key>>> creators = List.of(
                    (i) -> Map.entry("ED25519", FAKE_ED25519_KEY_INFOS[i].publicKey()),
                    (i) -> Map.entry("ECDSA_SECP256K1", FAKE_ECDSA_KEY_INFOS[i].publicKey()),
                    (i) -> Map.entry(
                            "KeyList(ECDSA_SECP256K1, ED25519)",
                            keyList(FAKE_ECDSA_KEY_INFOS[i].publicKey(), FAKE_ED25519_KEY_INFOS[i].publicKey())),
                    (i) -> Map.entry(
                            "ThresholdKey(1, ED25519, ECDSA_SECP256K1)",
                            thresholdKey(
                                    1, FAKE_ED25519_KEY_INFOS[i].publicKey(), FAKE_ECDSA_KEY_INFOS[i].publicKey())));

            // Compute every permutation of 1, 2, 3, and 4 elements.
            for (int i = -1; i < 4; i++) {
                for (int j = -1; j < 4; j++) {
                    for (int k = -1; k < 4; k++) {
                        for (int el = 0; el < 4; el++) {
                            int keyIndex = 0;
                            final var names = new ArrayList<String>();
                            final var keys = new ArrayList<Key>();
                            if (i >= 0) {
                                final var entry = creators.get(i).apply(keyIndex++);
                                final var name = entry.getKey();
                                final var key = entry.getValue();
                                names.add(name);
                                keys.add(key);
                            }
                            if (j >= 0) {
                                final var entry = creators.get(j).apply(keyIndex++);
                                final var name = entry.getKey();
                                final var key = entry.getValue();
                                names.add(name);
                                keys.add(key);
                            }
                            if (k >= 0) {
                                final var entry = creators.get(k).apply(keyIndex++);
                                final var name = entry.getKey();
                                final var key = entry.getValue();
                                names.add(name);
                                keys.add(key);
                            }
                            final var entry = creators.get(el).apply(keyIndex);
                            final var name = entry.getKey();
                            final var key = entry.getValue();
                            names.add(name);
                            keys.add(key);

                            final var keyList = KeyList.newBuilder().keys(keys).build();
                            map.put(String.join(", ", names), keyList);
                        }
                    }
                }
            }
            return map;
        }

        /** Provides all {@link SignatureVerificationFuture}s for every cryptographic key in the {@link Key}. */
        private static Map<Key, SignatureVerificationFuture> allVerifications(@NonNull final Key key) {
            return switch (key.key().kind()) {
                case KEY_LIST -> allVerifications(key.keyListOrThrow());
                case THRESHOLD_KEY -> allVerifications(key.thresholdKeyOrThrow().keysOrThrow());
                case ED25519, ECDSA_SECP256K1 -> new HashMap<>(Map.of(key, goodFuture(key))); // make mutable
                default -> throw new IllegalArgumentException(
                        "Unsupported key type: " + key.key().kind());
            };
        }

        /** Creates a {@link SignatureVerification} for each key in the key list */
        private static Map<Key, SignatureVerificationFuture> allVerifications(@NonNull final KeyList key) {
            return key.keysOrThrow().stream()
                    .map(FindingSignatureVerificationWithCompoundKeyTests::allVerifications)
                    .flatMap(map -> map.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        /**
         * Removes some number of {@link SignatureVerificationFuture}s from the map such that either there are only
         * just barely enough remaining to pass any threshold checks (if {@code removeTooMany} is {@code false}), or too
         * many of them such that there are not enough for threshold checks to pass (if {@code removeToMany} is
         * {@code true}).
         */
        private static void removeVerificationsFrom(
                @NonNull final Key key,
                @NonNull final Map<Key, SignatureVerificationFuture> map,
                final boolean removeTooMany) {

            switch (key.key().kind()) {
                case KEY_LIST -> {
                    // A Key list cannot have ANY removed and still pass. So we only remove a single key's worth of
                    // verifications if we are removing too many.
                    if (removeTooMany) {
                        final var subKeys = key.keyListOrThrow().keysOrThrow();
                        final var subKey = subKeys.get(0);
                        removeVerificationsFrom(subKey, map, true);
                    }
                }
                case THRESHOLD_KEY -> {
                    // We remove verifications associated with keys. If we are removing too many, we remove one more
                    // than is supported by the threshold. Otherwise, we just remove down to the threshold
                    final var threshold = key.thresholdKeyOrThrow().threshold();
                    final var subKeys = key.thresholdKeyOrThrow().keysOrThrow().keysOrThrow();
                    final var numToRemove = subKeys.size() - threshold + (removeTooMany ? 1 : 0);
                    for (int i = 0; i < numToRemove; i++) {
                        final var subKey = subKeys.get(i);
                        removeVerificationsFrom(subKey, map, removeTooMany);
                    }
                }
                case ED25519, ECDSA_SECP256K1 -> {
                    if (removeTooMany) {
                        map.remove(key);
                    }
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported key type: " + key.key().kind());
            }
        }

        /** Similar to the above, except we fail verifications instead of removing them. */
        private static void failVerificationsIn(
                @NonNull final Key key, @NonNull Map<Key, SignatureVerificationFuture> map, boolean failTooMany) {
            switch (key.key().kind()) {
                case KEY_LIST -> {
                    // A Key list cannot have ANY failed and still pass. So we only fail a single key's worth of
                    // verifications if we are failing too many.
                    if (failTooMany) {
                        final var subKeys = key.keyListOrThrow().keysOrThrow();
                        final var subKey = subKeys.get(0);
                        failVerificationsIn(subKey, map, true);
                    }
                }
                case THRESHOLD_KEY -> {
                    // We fail verifications associated with keys. If we are failing too many, we fail one more
                    // than is supported by the threshold. Otherwise, we just fail down to the threshold
                    final var threshold = key.thresholdKeyOrThrow().threshold();
                    final var subKeys = key.thresholdKeyOrThrow().keysOrThrow().keysOrThrow();
                    final var numToFail = subKeys.size() - threshold + (failTooMany ? 1 : 0);
                    for (int i = 0; i < numToFail; i++) {
                        final var subKey = subKeys.get(i);
                        failVerificationsIn(subKey, map, failTooMany);
                    }
                }
                case ED25519, ECDSA_SECP256K1 -> {
                    if (failTooMany) {
                        map.put(key, badFuture(key));
                    }
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported key type: " + key.key().kind());
            }
        }
    }

    @Nested
    @DisplayName("Hollow Account based Verification")
    final class HollowAccountBasedTest {
        /** As with key verification, with hollow account verification, an empty list of signatures should fail. */
        @Test
        @DisplayName("Cannot verify hollow account when the signature list is empty")
        void failToVerifyIfSignaturesAreEmpty() {
            // Given a hollow account and no verification results
            final var alias = ERIN.account().alias();
            // When we pre-handle the transaction
            final var result = preHandle(emptyMap());
            // Then we find the verification result is failed
            assertThat(result.verificationFor(alias))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        /** If there are verifications but none for this hollow account, then we get no result */
        @Test
        @DisplayName("Cannot verify hollow account if it is not in the verification results")
        void failToVerifyIfHollowAccountIsNotInVerificationResults() {
            // Given a hollow account and no verification results
            final var alias = ERIN.account().alias();
            Map<Key, SignatureVerificationFuture> verificationResults = Map.of(
                    ALICE.keyInfo().publicKey(), goodFuture(ALICE.keyInfo().publicKey()),
                    BOB.keyInfo().publicKey(), goodFuture(BOB.keyInfo().publicKey()),
                    CAROL.keyInfo().publicKey(), goodFuture(CAROL.keyInfo().publicKey(), CAROL.account()));
            // When we pre-handle the transaction
            final var result = preHandle(verificationResults);
            // Then we find the verification result is failed
            assertThat(result.verificationFor(alias))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(false);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Able to verify if the hollow account is in the verification results")
        void failToVerifyIfHollowAccountIsNotInVerificationResults(final boolean passes) {
            // Given a hollow account and no verification results
            final var alias = ERIN.account().alias();
            Map<Key, SignatureVerificationFuture> verificationResults = Map.of(
                    ALICE.keyInfo().publicKey(), goodFuture(ALICE.keyInfo().publicKey()),
                    BOB.keyInfo().publicKey(), goodFuture(BOB.keyInfo().publicKey()),
                    CAROL.keyInfo().publicKey(), goodFuture(CAROL.keyInfo().publicKey(), CAROL.account()),
                    ERIN.keyInfo().publicKey(),
                            passes
                                    ? goodFuture(ERIN.keyInfo().publicKey(), ERIN.account())
                                    : badFuture(ERIN.keyInfo().publicKey(), ERIN.account()));
            // When we pre-handle the transaction
            final var result = preHandle(verificationResults);
            // Then we find the verification result is as expected
            assertThat(result.verificationFor(alias))
                    .succeedsWithin(1, TimeUnit.SECONDS)
                    .extracting(SignatureVerification::passed)
                    .isEqualTo(passes);
        }
    }

    /** A simple utility method for creating a "SO_FAR_SO_GOOD" PreHandleResult */
    private PreHandleResult preHandle(@NonNull final Map<Key, SignatureVerificationFuture> map) {
        return new PreHandleResult(ALICE.accountID(), ALICE.account().key(), SO_FAR_SO_GOOD, OK, null, map, null);
    }

    /** Convenience method for creating a key list */
    private static Key keyList(Key... keys) {
        return Key.newBuilder().keyList(KeyList.newBuilder().keys(keys)).build();
    }

    /** Convenience method for creating a threshold key */
    private static Key thresholdKey(int threshold, Key... keys) {
        return Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder()
                        .keys(KeyList.newBuilder().keys(keys))
                        .threshold(threshold))
                .build();
    }

    /** Convenience method for creating a SignatureVerificationFuture that passes */
    private static FakeSignatureVerificationFuture goodFuture(@NonNull final Key key) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, null, true));
    }

    /** Convenience method for creating a SignatureVerificationFuture that passes */
    private static FakeSignatureVerificationFuture goodFuture(@NonNull final Key key, @NonNull final Account account) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, account.alias(), true));
    }

    /** Convenience method for creating a SignatureVerificationFuture that fails */
    private static FakeSignatureVerificationFuture badFuture(@NonNull final Key key) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, null, false));
    }

    /** Convenience method for creating a SignatureVerificationFuture that passes */
    private static FakeSignatureVerificationFuture badFuture(@NonNull final Key key, @NonNull final Account account) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, account.alias(), false));
    }

    /** A simple implementation of {@link SignatureVerificationFuture} that is backed by a {@link CompletableFuture} */
    private static final class FakeSignatureVerificationFuture extends CompletableFuture<SignatureVerification>
            implements SignatureVerificationFuture {

        private final SignatureVerification verification;

        private FakeSignatureVerificationFuture(@NonNull final SignatureVerification verification) {
            this.verification = verification;
            super.complete(verification);
        }

        @Nullable
        @Override
        public Bytes evmAlias() {
            return verification.evmAlias();
        }

        @NonNull
        @Override
        public Key key() {
            return requireNonNull(verification.key());
        }
    }
}