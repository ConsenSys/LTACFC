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
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetBlockTransactionCountByHash;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import tech.pegasys.ltacfc.common.AnIdentity;
import tech.pegasys.ltacfc.common.RevertReason;
import tech.pegasys.ltacfc.common.StatsHolder;
import tech.pegasys.ltacfc.common.Tuple;
import tech.pegasys.ltacfc.soliditywrappers.CbcSignedEvent;
import tech.pegasys.ltacfc.soliditywrappers.CbcTxRootTransfer;
import tech.pegasys.ltacfc.soliditywrappers.TxReceiptsRootStorage;
import tech.pegasys.poc.witnesscodeanalysis.trie.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.poc.witnesscodeanalysis.trie.ethereum.trie.Proof;
import tech.pegasys.poc.witnesscodeanalysis.trie.ethereum.trie.SimpleMerklePatriciaTrie;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CrossBlockchainControlTxReceiptRootTransfer extends AbstractCbc {
  private static final Logger LOG = LogManager.getLogger(CrossBlockchainControlTxReceiptRootTransfer.class);

  // TODO put this into a map for the current transaction id, so many transactions can be handled in parallel
  // The time-out for the current transaction.
  private long crossBlockchainTransactionTimeout;
  private boolean rootEventSuccess;

  private TxReceiptsRootStorage txReceiptsRootStorageContract;
  private CbcTxRootTransfer crossBlockchainControlContract;

  public CrossBlockchainControlTxReceiptRootTransfer(Credentials credentials, String bcId, String uri, String gasPriceStrategy, String blockPeriod) throws IOException {
    super(credentials, bcId, uri, gasPriceStrategy, blockPeriod);
  }


  public void deployContracts() throws Exception {
    super.deployContracts();
    this.txReceiptsRootStorageContract =
        TxReceiptsRootStorage.deploy(this.web3j, this.tm, this.gasProvider,
            this.registrarContract.getContractAddress()).send();
    this.crossBlockchainControlContract =
        CbcTxRootTransfer.deploy(this.web3j, this.tm, this.gasProvider,
            this.blockchainId, this.txReceiptsRootStorageContract.getContractAddress()).send();
    LOG.info(" TxReceiptRoot Contract: {}", this.txReceiptsRootStorageContract.getContractAddress());
    LOG.info(" Cross Blockchain Contract Contract: {}", this.crossBlockchainControlContract.getContractAddress());
  }



  public TransactionReceipt start(BigInteger transactionId, BigInteger timeout, byte[] callGraph) throws Exception {
    TransactionReceipt txR = this.crossBlockchainControlContract.start(transactionId, timeout, callGraph).send();
    StatsHolder.logGas("Start Transaction", txR.getGasUsed());
    List<CbcTxRootTransfer.StartEventResponse> startEvents = this.crossBlockchainControlContract.getStartEvents(txR);
    CbcTxRootTransfer.StartEventResponse startEvent = startEvents.get(0);
    this.crossBlockchainTransactionTimeout = startEvent._timeout.longValue();
    return txR;
  }

  public Tuple<TransactionReceipt, Boolean, Boolean> segment(
      TxReceiptRootTransferEventProof startProof,
      List<TxReceiptRootTransferEventProof> segProofs,
      List<BigInteger> callPath) throws Exception {

    List<byte[]> allProofs = new ArrayList<>();
    allProofs.add(startProof.getEncodedProof());

    for (TxReceiptRootTransferEventProof proofInfo: segProofs) {
      allProofs.add(proofInfo.getEncodedProof());
    }

    TransactionReceipt txR;
    try {
      txR = this.crossBlockchainControlContract.segment(allProofs, callPath).send();
      StatsHolder.logGas("Segment Transaction", txR.getGasUsed());
    }
    catch (TransactionException ex) {
      LOG.error(" Revert Reason: {}", RevertReason.decodeRevertReason(ex.getTransactionReceipt().get().getRevertReason()));
      throw ex;
    }
    if (!txR.isStatusOK()) {
      throw new Exception("Segment transaction failed");
    }


    List<CbcTxRootTransfer.SegmentEventResponse> segmentEventResponses = this.crossBlockchainControlContract.getSegmentEvents(txR);
    CbcTxRootTransfer.SegmentEventResponse segmentEventResponse = segmentEventResponses.get(0);
    LOG.info("Segment Event:");
    LOG.info(" Cross-Blockchain Transaction Id: {}", segmentEventResponse._crossBlockchainTransactionId.toString(16));
    StringBuilder calls = new StringBuilder();
    // TODO The code below is a hack to handle the fact that currently Web3J returns a Uint256 object, but the type is BigInteger.
    // TODO this code will break when Web3J fixes their bug.
    for (Object partOfCallPath: segmentEventResponse._callPath) {
      Uint256 hack = (Uint256) partOfCallPath;
      calls.append("[");
      calls.append(hack.getValue());
      calls.append("] ");
    }
    LOG.info(" Call Path: {}", calls);
    LOG.info(" Hash Of Call Graph: {}", new BigInteger(1, segmentEventResponse._hashOfCallGraph).toString(16));
    LOG.info(" Success: {}", segmentEventResponse._success);
    LOG.info(" Return Value: {}", new BigInteger(1, segmentEventResponse._returnValue).toString(16));
    StringBuilder lockedContracts = new StringBuilder();
    // TODO The code below is a hack to handle the fact that currently Web3J returns an Address object, but the type is BigInteger.
    // TODO this code will break when Web3J fixes their bug.
    for (Object lockedContract: segmentEventResponse._lockedContracts) {
      Address hack = (Address) lockedContract;
      calls.append("[");
      calls.append(hack.getValue());
      calls.append("] ");
    }
    LOG.info(" Locked Contracts: [{}]", lockedContracts);

    showAllCallEvents(txR);
    showAllNotEnoughCallsEvents(txR);
    showAllDumpEvents(txR);

    return new Tuple<TransactionReceipt, Boolean, Boolean>
        (txR, segmentEventResponse._lockedContracts.isEmpty(), segmentEventResponse._success);
  }


  public TransactionReceipt root(TxReceiptRootTransferEventProof startProof, List<TxReceiptRootTransferEventProof> segProofs) throws Exception {
    List<byte[]> allProofs = new ArrayList<>();
    allProofs.add(startProof.getEncodedProof());

    for (TxReceiptRootTransferEventProof proofInfo: segProofs) {
      allProofs.add(proofInfo.getEncodedProof());
    }

    long now = System.currentTimeMillis() / 1000;
    LOG.info(" Current time on this computer: {}; Transaction time-out: {}", now, this.crossBlockchainTransactionTimeout);
    if (this.crossBlockchainTransactionTimeout < now) {
      LOG.warn(" Cross-Blockchain transaction will fail as transaction has timed-out");
    }
    else if (this.crossBlockchainTransactionTimeout < (now - 10)) {
      LOG.warn(" Cross-Blockchain transaction might fail as transaction time-out is soon");
    }

    TransactionReceipt txR;
    try {
      txR = this.crossBlockchainControlContract.root(allProofs).send();
      StatsHolder.logGas("Root Transaction", txR.getGasUsed());
    }
    catch (TransactionException ex) {
      LOG.error(" Revert Reason: {}", RevertReason.decodeRevertReason(ex.getTransactionReceipt().get().getRevertReason()));
      throw ex;
    }
    if (!txR.isStatusOK()) {
      throw new Exception("Root transaction failed");
    }

    List<CbcTxRootTransfer.RootEventResponse> rootEventResponses = this.crossBlockchainControlContract.getRootEvents(txR);
    CbcTxRootTransfer.RootEventResponse rootEventResponse = rootEventResponses.get(0);
    LOG.info("Root Event:");
    LOG.info(" _crossBlockchainTransactionId: {}", rootEventResponse._crossBlockchainTransactionId.toString(16));
    LOG.info(" _success: {}", rootEventResponse._success);
    this.rootEventSuccess = rootEventResponse._success;

    showAllCallEvents(txR);
    showAllNotEnoughCallsEvents(txR);
    showAllDumpEvents(txR);

    return txR;
  }




  public void signalling(TxReceiptRootTransferEventProof rootProof, List<TxReceiptRootTransferEventProof> segProofs) throws Exception {
    List<byte[]> allProofs = new ArrayList<>();
    allProofs.add(rootProof.getEncodedProof());

    for (TxReceiptRootTransferEventProof proofInfo: segProofs) {
      allProofs.add(proofInfo.getEncodedProof());
    }

    TransactionReceipt txR;
    try {
      txR = this.crossBlockchainControlContract.signalling(allProofs).send();
      StatsHolder.logGas("Signalling Transaction", txR.getGasUsed());
    }
    catch (TransactionException ex) {
      LOG.error(" Revert Reason: {}", RevertReason.decodeRevertReason(ex.getTransactionReceipt().get().getRevertReason()));
      throw ex;
    }
    if (!txR.isStatusOK()) {
      throw new Exception("Signalling transaction failed");
    }

    List<CbcTxRootTransfer.SignallingEventResponse> sigEventResponses = this.crossBlockchainControlContract.getSignallingEvents(txR);
    CbcTxRootTransfer.SignallingEventResponse sigEventResponse = sigEventResponses.get(0);
    LOG.info("Signalling Event:");
    LOG.info(" Root Blockchain Id: 0x{}", sigEventResponse._rootBcId.toString(16));
    LOG.info(" Cross-Blockchain Transaction Id: {}", sigEventResponse._crossBlockchainTransactionId.toString(16));

    showAllDumpEvents(txR);
  }



  public String getCbcContractAddress() {
    return this.crossBlockchainControlContract.getContractAddress();
  }

  public boolean getRootEventSuccess() {
    return this.rootEventSuccess;
  }



  public byte[] getTransactionReceiptRoot(TransactionReceipt transactionReceipt) throws Exception {
    EthBlock block = this.web3j.ethGetBlockByHash(transactionReceipt.getBlockHash(), false).send();
    EthBlock.Block b1 = block.getBlock();
    String receiptsRoot = b1.getReceiptsRoot();
    Bytes32 receiptsRootBytes32 = Bytes32.fromHexString(receiptsRoot);
    return receiptsRootBytes32.toArray();
  }


  public void addTransactionReceiptRootToBlockchain(
      AnIdentity[] signers, BigInteger sourceBlockchainId, byte[] transactionReceiptRoot) throws Exception {
    // Add the transaction receipt root for the blockchain
    // Sign the txReceiptRoot
    List<String> theSigners = new ArrayList<>();
    List<byte[]> sigR = new ArrayList<>();
    List<byte[]> sigS = new ArrayList<>();
    List<BigInteger> sigV = new ArrayList<>();
    for (AnIdentity signer: signers) {
      Sign.SignatureData signatureData = signer.sign(transactionReceiptRoot);
      theSigners.add(signer.getAddress());
      sigR.add(signatureData.getR());
      sigS.add(signatureData.getS());
      sigV.add(BigInteger.valueOf(signatureData.getV()[0]));
    }

    // This will revert if the signature does not verify
    try {
      TransactionReceipt txR = this.txReceiptsRootStorageContract.addTxReceiptRoot(sourceBlockchainId, theSigners, sigR, sigS, sigV, transactionReceiptRoot).send();
      StatsHolder.logGas("AddTxReceiptRoot Transaction", txR.getGasUsed());
      if (!txR.isStatusOK()) {
        throw new Exception("Transaction to add transaction receipt root failed");
      }
    } catch (TransactionException txe) {
      String revertReason = txe.getTransactionReceipt().get().getRevertReason();
      LOG.error("Revert Reason: {}", RevertReason.decodeRevertReason(revertReason));
      throw txe;
    }
  }


  public TxReceiptRootTransferEventProof getProofForTxReceipt(BigInteger blockchainId, String cbcContractAddress, TransactionReceipt aReceipt) throws Exception {
    // Calculate receipt root based on logs for all receipts of all transactions in the block.
    String blockHash = aReceipt.getBlockHash();
    EthGetBlockTransactionCountByHash transactionCountByHash = this.web3j.ethGetBlockTransactionCountByHash(blockHash).send();
    BigInteger txCount = transactionCountByHash.getTransactionCount();

    List<org.hyperledger.besu.ethereum.core.TransactionReceipt> besuReceipts = new ArrayList<>();

    BigInteger transactionIndex = BigInteger.ZERO;
    do {
      EthTransaction ethTransaction = this.web3j.ethGetTransactionByBlockHashAndIndex(blockHash, transactionIndex).send();
      Optional<Transaction> transaction = ethTransaction.getTransaction();
      assert(transaction.isPresent());
      String txHash = transaction.get().getHash();
      EthGetTransactionReceipt ethGetTransactionReceipt = this.web3j.ethGetTransactionReceipt(txHash).send();
      Optional<TransactionReceipt> mayBeReceipt = ethGetTransactionReceipt.getTransactionReceipt();
      assert(mayBeReceipt.isPresent());
      TransactionReceipt receipt = mayBeReceipt.get();

      // Convert to Besu objects
      List<org.hyperledger.besu.ethereum.core.Log> besuLogs = new ArrayList<>();

      String stateRootFromReceipt = receipt.getRoot();
      Hash root = (stateRootFromReceipt == null) ? null : Hash.fromHexString(receipt.getRoot());
      String statusFromReceipt = receipt.getStatus();
      int status = statusFromReceipt == null ? -1 : Integer.parseInt(statusFromReceipt.substring(2), 16);
      for (Log web3jLog: receipt.getLogs()) {
        org.hyperledger.besu.ethereum.core.Address addr = org.hyperledger.besu.ethereum.core.Address.fromHexString(web3jLog.getAddress());
        Bytes data = Bytes.fromHexString(web3jLog.getData());
        List<String> topics = web3jLog.getTopics();
        List<LogTopic> logTopics = new ArrayList<>();
        for (String topic: topics) {
          LogTopic logTopic = LogTopic.create(Bytes.fromHexString(topic));
          logTopics.add(logTopic);
        }
        besuLogs.add(new org.hyperledger.besu.ethereum.core.Log(addr, data, logTopics));
      }
      String revertReasonFromReceipt = receipt.getRevertReason();
      Bytes revertReason = revertReasonFromReceipt == null ? null : Bytes.fromHexString(receipt.getRevertReason());
      org.hyperledger.besu.ethereum.core.TransactionReceipt txReceipt =
          root == null ?
              new org.hyperledger.besu.ethereum.core.TransactionReceipt(status, receipt.getCumulativeGasUsed().longValue(),
                  besuLogs, Optional.ofNullable(revertReason))
              :
              new org.hyperledger.besu.ethereum.core.TransactionReceipt(root, receipt.getCumulativeGasUsed().longValue(),
                  besuLogs, Optional.ofNullable(revertReason));
      besuReceipts.add(txReceipt);

      // Increment for the next time through the loop.
      transactionIndex = transactionIndex.add(BigInteger.ONE);
    } while (transactionIndex.compareTo(txCount) != 0);

    final MerklePatriciaTrie<Bytes, Bytes> trie = trie();
    for (int i = 0; i < besuReceipts.size(); ++i) {
      Bytes rlpEncoding = RLP.encode(besuReceipts.get(i)::writeTo);
      trie.put(indexKey(i), rlpEncoding);
    }
    Bytes32 besuCalculatedReceiptsRoot = trie.getRootHash();
    String besuCalculatedReceiptsRootStr = besuCalculatedReceiptsRoot.toHexString();

    // TODO remove this check code that isn't needed
    EthBlock block = this.web3j.ethGetBlockByHash(aReceipt.getBlockHash(), false).send();
    EthBlock.Block b1 = block.getBlock();
    String receiptsRoot = b1.getReceiptsRoot();
    if (!besuCalculatedReceiptsRootStr.equalsIgnoreCase( receiptsRoot)) {
      LOG.error("Calculated transaction receipt root {} does not match actual receipt root {}", besuCalculatedReceiptsRootStr, receiptsRoot);
      throw new Error("Calculated transaction receipt root does not match actual receipt root");
    }

//    // TODO remove
//    Bytes32 parentHash = Bytes32.fromHexString(b1.getParentHash());
//    Bytes32 ommersHash = Bytes32.fromHexString(b1.getSha3Uncles());
//    Bytes coinbase = Bytes.fromHexString(b1.getMiner());
//    Bytes32 stateRoot = Bytes32.fromHexString(b1.getStateRoot());
//    Bytes32 transactionsRoot = Bytes32.fromHexString(b1.getTransactionsRoot());
//    Bytes32 receiptsRoot1 = Bytes32.fromHexString(b1.getReceiptsRoot());
//    Bytes logsBloom = Bytes.fromHexString(b1.getLogsBloom());
//    BigInteger difficulty = b1.getDifficulty();
//    BigInteger number = b1.getNumber();
//    BigInteger gasLimit = b1.getGasLimit();
//    BigInteger gasUsed = b1.getGasUsed();
//    BigInteger timestamp = b1.getTimestamp();
//    Bytes extraData = Bytes.fromHexString(b1.getExtraData());
//    Bytes32 mixHash = Bytes32.fromHexString(b1.getMixHash());
//    BigInteger nonce = b1.getNonce();
//
//    Bytes blockHash1 = Hash.hash(
//        RLP.encode(
//            out -> {
//              out.startList();
//              out.writeBytes(parentHash);
//              out.writeBytes(ommersHash);
//              out.writeBytes(coinbase);
//              out.writeBytes(stateRoot);
//              out.writeBytes(transactionsRoot);
//              out.writeBytes(receiptsRoot1);
//              out.writeBytes(logsBloom);
//              out.writeBytes(UInt256.valueOf(difficulty).toMinimalBytes());
//              out.writeLongScalar(number.longValue());
//              out.writeLongScalar(gasLimit.longValue());
//              out.writeLongScalar(gasUsed.longValue());
//              out.writeLongScalar(timestamp.longValue());
//              out.writeBytes(extraData);
//              out.writeBytes(mixHash);
//              out.writeLong(nonce.longValue());
////    if (ExperimentalEIPs.eip1559Enabled && baseFee != null) {
////      out.writeLongScalar(baseFee);
////    }
//              out.endList();
//            }));
//    LOG.info("Block Hash Calculated***: {} should be: {}", blockHash1.toHexString(), blockHash);


    // TODO end remove

    BigInteger txIndex = aReceipt.getTransactionIndex();
    Bytes aKey = indexKey((int)txIndex.longValue());

    Proof<Bytes> simpleProof = trie.getValueWithSimpleProof(aKey);
    Bytes encodedTransactionReceipt = simpleProof.getValue().get();
    Bytes rlpOfNode = encodedTransactionReceipt;
    // Node references can be hashes or the node itself, if the node is less than 32 bytes.
    // Leaf nodes in Ethereum, leaves of Merkle Patricia Tries could be less than 32 bytes,
    // but no other nodes. For transaction receipts, it isn't possible even the leaf nodes
    // to be 32 bytes.
    Bytes32 nodeHash = org.hyperledger.besu.crypto.Hash.keccak256(rlpOfNode);

    List<Bytes> proofList1 = simpleProof.getProofRelatedNodes();
    List<BigInteger> proofOffsets = new ArrayList<>();
    List<byte[]> proofs = new ArrayList<>();
    for (int j = proofList1.size()-1; j >=0; j--) {
      rlpOfNode = proofList1.get(j);
      proofOffsets.add(BigInteger.valueOf(findOffset(rlpOfNode, nodeHash)));
      proofs.add(rlpOfNode.toArray());
      nodeHash = org.hyperledger.besu.crypto.Hash.keccak256(rlpOfNode);
    }
    if (!besuCalculatedReceiptsRoot.toHexString().equalsIgnoreCase(nodeHash.toHexString())) {
      throw new Error("Transaction receipt root calculated using proof did not match actual receipt root");
    }
    return new TxReceiptRootTransferEventProof(
        blockchainId,
        cbcContractAddress,
        getTransactionReceiptRoot(aReceipt),
        encodedTransactionReceipt.toArray(),
        proofOffsets,
        proofs);
  }







  protected static int findOffset(Bytes rlpOfNode, Bytes nodeRef) {
    int sizeNodeRef = nodeRef.size();
    for (int i = 0; i < rlpOfNode.size() - sizeNodeRef; i++) {
      boolean found = true;
      for (int j = 0; j < sizeNodeRef; j++) {
        if (rlpOfNode.get(i+j) != nodeRef.get(j)) {
          found = false;
          break;
        }
      }
      if (found) {
        return i;
      }
    }
    return -1;
  }


  protected static MerklePatriciaTrie<Bytes, Bytes> trie() {
    return new SimpleMerklePatriciaTrie<>(b -> b);
  }

  protected static Bytes indexKey(final int i) {
    return RLP.encodeOne(UInt256.valueOf(i).toBytes().trimLeadingZeros());
  }

  public TxReceiptRootTransferEventProof getStartEventProof(TransactionReceipt startTxReceipt) throws Exception {
    return getProofForTxReceipt(this.blockchainId, this.crossBlockchainControlContract.getContractAddress(), startTxReceipt);
  }
  public TxReceiptRootTransferEventProof getRootEventProof(TransactionReceipt rootTxReceipt) throws Exception {
    return getProofForTxReceipt(this.blockchainId, this.crossBlockchainControlContract.getContractAddress(), rootTxReceipt);
  }
  public TxReceiptRootTransferEventProof getSegmentEventProof(TransactionReceipt segmentTxReceipt) throws Exception {
    return getProofForTxReceipt(this.blockchainId, this.crossBlockchainControlContract.getContractAddress(), segmentTxReceipt);
  }

  protected void showAllCallEvents(TransactionReceipt txR) {
    LOG.info("Call Events");
    List<CbcTxRootTransfer.CallEventResponse> callEventResponses = this.crossBlockchainControlContract.getCallEvents(txR);
    if (callEventResponses.isEmpty()) {
      LOG.info(" None");
    }
    for (CbcTxRootTransfer.CallEventResponse callEventResponse : callEventResponses) {
      LOG.info(" Event:");
      LOG.info("   Expected Blockchain Id: 0x{}", callEventResponse._expectedBlockchainId.toString(16));
      LOG.info("   Actual Blockchain Id: 0x{}", callEventResponse._actualBlockchainId.toString(16));
      LOG.info("   Expected Contract: {}", callEventResponse._expectedContract);
      LOG.info("   Actual Contract: {}", callEventResponse._actualContract);
      LOG.info("   Expected Function Call: {}", new BigInteger(1, callEventResponse._expectedFunctionCall).toString(16));
      LOG.info("   Actual Function Call: {}", new BigInteger(1, callEventResponse._actualFunctionCall).toString(16));
      LOG.info("   Return Value: {}", new BigInteger(1, callEventResponse._retVal).toString(16));
    }
  }


  protected void showAllNotEnoughCallsEvents(TransactionReceipt txR) {
    LOG.info("Not Enough Call Events");
    List<CbcTxRootTransfer.NotEnoughCallsEventResponse> notEnoughCallsEventResponses = this.crossBlockchainControlContract.getNotEnoughCallsEvents(txR);
    if (notEnoughCallsEventResponses.isEmpty()) {
      LOG.info(" None");
    }
    for (CbcTxRootTransfer.NotEnoughCallsEventResponse notEnoughCallsEventResponse: notEnoughCallsEventResponses) {
      LOG.info("  Event:");
      LOG.info("   Actual Number of Calls: {}", notEnoughCallsEventResponse._actualNumberOfCalls);
      LOG.info("   Expected Number of Calls: {}", notEnoughCallsEventResponse._expectedNumberOfCalls);
    }
  }

  protected void showAllDumpEvents(TransactionReceipt txR) {
    LOG.info("Dump Events");
    List<CbcTxRootTransfer.DumpEventResponse> dumpEventResponses = this.crossBlockchainControlContract.getDumpEvents(txR);
    if (dumpEventResponses.isEmpty()) {
      LOG.info(" None");
    }
    for (CbcTxRootTransfer.DumpEventResponse dumpEventResponse : dumpEventResponses) {
      LOG.info(" Event:");
      LOG.info("  Uint256: {}", dumpEventResponse._val1.toString(16));
      LOG.info("  Bytes32: {}", new BigInteger(1, dumpEventResponse._val2).toString(16));
      LOG.info("  Address: {}", dumpEventResponse._val3);
      LOG.info("  Bytes: {}", new BigInteger(1, dumpEventResponse._val4).toString(16));
    }
  }

}
