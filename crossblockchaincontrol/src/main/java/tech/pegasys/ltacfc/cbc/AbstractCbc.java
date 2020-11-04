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
package tech.pegasys.ltacfc.cbc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.Hash;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.ltacfc.common.AnIdentity;
import tech.pegasys.ltacfc.registrar.RegistrarVoteTypes;
import tech.pegasys.ltacfc.soliditywrappers.CbcSignedEvent;
import tech.pegasys.ltacfc.soliditywrappers.Registrar;

import java.io.IOException;
import java.math.BigInteger;
import java.security.DrbgParameters;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

import static java.security.DrbgParameters.Capability.RESEED_ONLY;

public abstract class AbstractCbc extends AbstractBlockchain {
  private static final Logger LOG = LogManager.getLogger(AbstractCbc.class);

  public static byte[] START_EVENT_SIGNATURE = Hash.keccak256(Bytes.wrap("Start(uint256,address,uint256,bytes)".getBytes())).toArray();
  public static Bytes START_EVENT_SIGNATURE_BYTES = Bytes.wrap(START_EVENT_SIGNATURE);
  public static byte[] SEGMENT_EVENT_SIGNATURE = Hash.keccak256(Bytes.wrap("Segment(uint256,bytes32,uint256[],address[],bool,bytes)".getBytes())).toArray();
  public static Bytes SEGMENT_EVENT_SIGNATURE_BYTES = Bytes.wrap(SEGMENT_EVENT_SIGNATURE);
  public static byte[] ROOT_EVENT_SIGNATURE = Hash.keccak256(Bytes.wrap("Root(uint256,bool)".getBytes())).toArray();
  public static Bytes ROOT_EVENT_SIGNAUTRE_BYTES = Bytes.wrap(ROOT_EVENT_SIGNATURE);


  Registrar registrarContract;


  protected AbstractCbc(Credentials credentials, String bcId, String uri, String gasPriceStrategy, String blockPeriod) throws IOException {
      super(credentials, bcId, uri, gasPriceStrategy, blockPeriod);
  }

  protected void deployContracts() throws Exception {
    this.registrarContract = Registrar.deploy(this.web3j, this.tm, this.gasProvider).send();
    LOG.info(" Registrar Contract: {}", this.registrarContract.getContractAddress());
  }

  public void registerSignerThisBlockchain(AnIdentity signer) throws Exception {
    registerSigner(signer, this.blockchainId);
  }

  public void registerSigner(AnIdentity signer, BigInteger bcId) throws Exception {
    LOG.info("Registering signer 0x{} as signer for blockchain 0x{} in registration contract on blockchain 0x{}",
        signer.getAddress(), bcId.toString(16), this.blockchainId.toString(16));
    TransactionReceipt receipt1 = this.registrarContract.proposeVote(
        RegistrarVoteTypes.VOTE_ADD_SIGNER.asBigInt(), bcId, signer.getAddressAsBigInt()).send();
    if (!receipt1.isStatusOK()) {
      throw new Exception("Transaction to register signer failed");
    }
  }

  public static BigInteger generateRandomCrossBlockchainTransactionId() throws NoSuchAlgorithmException {
    // TODO put this into the crypto module and do a better job or this + reseeding.
    final SecureRandom rand = SecureRandom.getInstance("DRBG",
        DrbgParameters.instantiation(256, RESEED_ONLY, new byte[]{0x01}));
    return new BigInteger(255, rand);
  }

  public abstract String getCbcContractAddress();
}
