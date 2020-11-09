/*
 * Copyright 2020 ConsenSys AG.
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
package tech.pegasys.ltacfc.cbc.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.ltacfc.cbc.CbcManager;
import tech.pegasys.ltacfc.cbc.CrossBlockchainControlTxReceiptRootTransfer;
import tech.pegasys.ltacfc.cbc.TxReceiptRootTransferEventProof;
import tech.pegasys.ltacfc.common.AnIdentity;
import tech.pegasys.ltacfc.common.CrossBlockchainConsensus;
import tech.pegasys.ltacfc.common.Tuple;

import javax.sound.midi.Track;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CbcExecutorTxReceiptRootTransfer extends AbstractCbcExecutor {
  static final Logger LOG = LogManager.getLogger(CbcExecutorTxReceiptRootTransfer.class);

  TxReceiptRootTransferEventProof startProof;
  TxReceiptRootTransferEventProof rootProof;

  // Key for this map is the call path of the caller.
  Map<BigInteger, List<TxReceiptRootTransferEventProof>> segmentProofs = new HashMap<>();

  // Key for this map is the blockchain id that the segment occurred on.
  Map<BigInteger, List<TxReceiptRootTransferEventProof>> segmentProofsWithLockedContracts = new HashMap<>();

  public CbcExecutorTxReceiptRootTransfer(CbcManager cbcManager) throws Exception {
    super(CrossBlockchainConsensus.TRANSACTION_RECEIPT_SIGNING, cbcManager);
  }


  protected void startCall() throws Exception {
    CrossBlockchainControlTxReceiptRootTransfer rootCbcContract = this.cbcManager.getCbcContractTxRootTransfer(this.rootBcId);

    TransactionReceipt startTxReceipt = rootCbcContract.start(this.crossBlockchainTransactionId, this.timeout, this.callGraph);
    this.startProof = rootCbcContract.getStartEventProof(startTxReceipt);
    publishReceiptRootToAll(this.rootBcId, this.startProof.getTransactionReceiptRoot());
  }

  protected void segment(BigInteger blockchainId, BigInteger callerBlockchainId, List<BigInteger> callPath) throws Exception {
    if (callPath.size() == 0) {
      throw new Exception("Invalid call path length for segment: " + callPath.size());
    }

    BigInteger mapKey = callPathToMapKey(callPath);

    CrossBlockchainControlTxReceiptRootTransfer segmentCbcContract = this.cbcManager.getCbcContractTxRootTransfer(blockchainId);

    List<TxReceiptRootTransferEventProof> proofs = this.segmentProofs.computeIfAbsent(mapKey, k -> new ArrayList<>());
    Tuple<TransactionReceipt, Boolean, Boolean> result = segmentCbcContract.segment(this.startProof, proofs, callPath);
    TransactionReceipt segTxReceipt = result.getFirst();
    boolean noContractsLocked = result.getSecond();
    //boolean success = result.getThird();
    TxReceiptRootTransferEventProof segProof = segmentCbcContract.getSegmentEventProof(segTxReceipt);

    // Add the proof for the call that has just occurred to the map so it can be accessed when the next
    BigInteger parentMapKey = determineMapKeyOfCaller(callPath);
    proofs = this.segmentProofs.computeIfAbsent(parentMapKey, k -> new ArrayList<>());
    proofs.add(segProof);

    // Add the proof if there were locked contracts as a result of the segment.
    if (!noContractsLocked) {
      List<TxReceiptRootTransferEventProof> proofsOfSegmnentsWithLockedContracts =
          this.segmentProofsWithLockedContracts.computeIfAbsent(blockchainId, k -> new ArrayList<>());
      proofsOfSegmnentsWithLockedContracts.add(segProof);
    }

    // Segments proofs need to be available on the blockchain they executed on (for the
    // Signalling call), and on the blockchain that the contract that called this contract
    // resides on (for the Root or Segment call).
    Set<BigInteger> blockchainsToPublishTo = new HashSet<>();
    blockchainsToPublishTo.add(blockchainId);
    blockchainsToPublishTo.add(callerBlockchainId);
    publishReceiptRoot(blockchainId, segProof.getTransactionReceiptRoot(), blockchainsToPublishTo);
  }


  protected void root() throws Exception {
    CrossBlockchainControlTxReceiptRootTransfer rootCbcContract = this.cbcManager.getCbcContractTxRootTransfer(this.rootBcId);
    List<TxReceiptRootTransferEventProof> proofs = this.segmentProofs.get(ROOT_CALL_MAP_KEY);
    TransactionReceipt rootTxReceipt = rootCbcContract.root(this.startProof, proofs);
    this.rootProof = rootCbcContract.getRootEventProof(rootTxReceipt);

    this.success = rootCbcContract.getRootEventSuccess();
    publishReceiptRootToAll(this.rootBcId, this.rootProof.getTransactionReceiptRoot());
  }

  protected void doSignallingCalls() throws Exception {
    for (BigInteger blockchainId: this.segmentProofsWithLockedContracts.keySet()) {
      List<TxReceiptRootTransferEventProof> segProofsLockedContractsCurrentBlockchain =
          this.segmentProofsWithLockedContracts.get(blockchainId);
      CrossBlockchainControlTxReceiptRootTransfer cbcContract = this.cbcManager.getCbcContractTxRootTransfer(blockchainId);
      cbcContract.signalling(this.rootProof, segProofsLockedContractsCurrentBlockchain);
    }
  }


  private void publishReceiptRootToAll(BigInteger publishingFrom,  byte[] transactionReceiptRoot) throws Exception {
    Set<BigInteger> blockchainIdsToPublishTo = this.cbcManager.getAllBlockchainIds();
    publishReceiptRoot(publishingFrom, transactionReceiptRoot, blockchainIdsToPublishTo);
  }

  // Add tx receipt root so event will be trusted.
  private void publishReceiptRoot(BigInteger publishingFrom,  byte[] transactionReceiptRoot, Set<BigInteger> blockchainsToPublishTo) throws Exception {
    for (BigInteger bcId: blockchainsToPublishTo) {
      CrossBlockchainControlTxReceiptRootTransfer cbcContract = this.cbcManager.getCbcContractTxRootTransfer(bcId);
      AnIdentity[] signers = this.cbcManager.getSigners(bcId);
      cbcContract.addTransactionReceiptRootToBlockchain(signers, publishingFrom, transactionReceiptRoot);
    }
  }
}
