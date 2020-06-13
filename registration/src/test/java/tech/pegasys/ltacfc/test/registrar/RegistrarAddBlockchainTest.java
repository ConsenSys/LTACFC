/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.pegasys.ltacfc.test.registrar;

import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.TransactionManager;
import tech.pegasys.ltacfc.registrar.RegistrarVoteTypes;
import tech.pegasys.ltacfc.registrar.SigAlgorithmTypes;
import tech.pegasys.ltacfc.soliditywrappers.Registrar;

import java.math.BigInteger;

import static junit.framework.TestCase.assertFalse;

public class RegistrarAddBlockchainTest extends AbstractRegistrarTest {

  @Test
  public void addBlockchain() throws Exception {
    setupWeb3();
    deployContract();

    BigInteger blockchainId = BigInteger.TEN;
    BigInteger sigAlgorithm = SigAlgorithmTypes.ALG_ECDSA_KECCAK256_SECP256K1.asBigInt();

    TransactionReceipt receipt = this.contract.proposeVote(
        RegistrarVoteTypes.VOTE_ADD_BLOCKCHAIN.asBigInt(), blockchainId, sigAlgorithm).send();
    assert(receipt.isStatusOK());

    // Check the signature algorithm has been set correctly.
    assert(this.contract.getSigAlgorithm(blockchainId).send().compareTo(sigAlgorithm) == 0);
    // The initial signing threshold is one.
    assert(this.contract.getSigningThreshold(blockchainId).send().compareTo(BigInteger.ONE) == 0);
  }


  // Do not allow an admin to be added twice.
  @Test
  public void failAddSameBlockchainTwice() throws Exception {
    setupWeb3();
    deployContract();

    BigInteger blockchainId = BigInteger.TEN;
    BigInteger sigAlgorithm = SigAlgorithmTypes.ALG_ECDSA_KECCAK256_SECP256K1.asBigInt();

    TransactionReceipt receipt = this.contract.proposeVote(
        RegistrarVoteTypes.VOTE_ADD_BLOCKCHAIN.asBigInt(), blockchainId, sigAlgorithm).send();
    assert(receipt.isStatusOK());
    
    try {
      receipt = this.contract.proposeVote(
          RegistrarVoteTypes.VOTE_ADD_BLOCKCHAIN.asBigInt(), blockchainId, sigAlgorithm).send();
      assertFalse(receipt.isStatusOK());
    } catch (TransactionException ex) {
      // Do nothing.
    }

    // The initial signing threshold is one.
    assert(this.contract.getSigningThreshold(blockchainId).send().compareTo(BigInteger.ONE) == 0);
  }

}
