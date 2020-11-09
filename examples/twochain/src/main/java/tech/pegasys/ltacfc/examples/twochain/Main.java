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
package tech.pegasys.ltacfc.examples.twochain;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpType;
import tech.pegasys.ltacfc.cbc.AbstractCbc;
import tech.pegasys.ltacfc.cbc.CrossBlockchainControlSignedEvents;
import tech.pegasys.ltacfc.cbc.CrossBlockchainControlTxReceiptRootTransfer;
import tech.pegasys.ltacfc.cbc.SignedEvent;
import tech.pegasys.ltacfc.cbc.TxReceiptRootTransferEventProof;
import tech.pegasys.ltacfc.common.AnIdentity;
import tech.pegasys.ltacfc.common.CrossBlockchainConsensus;
import tech.pegasys.ltacfc.common.PropertiesLoader;
import tech.pegasys.ltacfc.common.StatsHolder;
import tech.pegasys.ltacfc.common.Tuple;
import tech.pegasys.ltacfc.examples.twochain.sim.SimOtherContract;
import tech.pegasys.ltacfc.examples.twochain.sim.SimRootContract;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static tech.pegasys.ltacfc.cbc.CallGraphHelper.createLeafFunctionCall;
import static tech.pegasys.ltacfc.cbc.CallGraphHelper.createRootFunctionCall;

public class Main {
  static final Logger LOG = LogManager.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    StatsHolder.log("Start");
    LOG.info("Started");

    if (args.length != 1) {
      LOG.info("Usage: [properties file name]");
      return;
    }

    PropertiesLoader propsLoader = new PropertiesLoader(args[0]);
    Credentials creds = propsLoader.getCredentials();
    PropertiesLoader.BlockchainInfo root = propsLoader.getBlockchainInfo("ROOT");
    PropertiesLoader.BlockchainInfo other = propsLoader.getBlockchainInfo("OTHER");
    CrossBlockchainConsensus consensusMethodology = propsLoader.getConsensusMethodology();
    StatsHolder.log(consensusMethodology.name());

    RootBc rootBlockchain = new RootBc(creds, root.bcId, root.uri, root.gasPriceStrategy, root.period);
    OtherBc otherBlockchain = new OtherBc(creds, other.bcId, other.uri, other.gasPriceStrategy, other.period);

    CrossBlockchainControlTxReceiptRootTransfer otherBlockchainCbcTxRootTransfer = null;
    CrossBlockchainControlTxReceiptRootTransfer rootBlockchainCbcTxRootTransfer = null;
    CrossBlockchainControlSignedEvents otherBlockchainCbcSignedEvents = null;
    CrossBlockchainControlSignedEvents rootBlockchainCbcSignedEvents = null;
    AbstractCbc otherBlockchainCbc = null;
    AbstractCbc rootBlockchainCbc = null;
    String otherBlockchainCbcContractAddress;
    String rootBlockchainCbcContractAddress;

    switch (consensusMethodology) {
      case TRANSACTION_RECEIPT_SIGNING:
        rootBlockchainCbcTxRootTransfer = new CrossBlockchainControlTxReceiptRootTransfer(creds, root.bcId, root.uri, root.gasPriceStrategy, root.period);
        otherBlockchainCbcTxRootTransfer = new CrossBlockchainControlTxReceiptRootTransfer(creds, other.bcId, other.uri, other.gasPriceStrategy, other.period);
        rootBlockchainCbcTxRootTransfer.deployContracts();
        otherBlockchainCbcTxRootTransfer.deployContracts();
        rootBlockchainCbcContractAddress = rootBlockchainCbcTxRootTransfer.getCbcContractAddress();
        otherBlockchainCbcContractAddress = otherBlockchainCbcTxRootTransfer.getCbcContractAddress();
        rootBlockchainCbc = rootBlockchainCbcTxRootTransfer;
        otherBlockchainCbc = otherBlockchainCbcTxRootTransfer;
        break;
      case EVENT_SIGNING:
        rootBlockchainCbcSignedEvents = new CrossBlockchainControlSignedEvents(creds, root.bcId, root.uri, root.gasPriceStrategy, root.period);
        otherBlockchainCbcSignedEvents = new CrossBlockchainControlSignedEvents(creds, other.bcId, other.uri, other.gasPriceStrategy, other.period);
        rootBlockchainCbcSignedEvents.deployContracts();
        otherBlockchainCbcSignedEvents.deployContracts();
        rootBlockchainCbcContractAddress = rootBlockchainCbcSignedEvents.getCbcContractAddress();
        otherBlockchainCbcContractAddress = otherBlockchainCbcSignedEvents.getCbcContractAddress();
        rootBlockchainCbc = rootBlockchainCbcSignedEvents;
        otherBlockchainCbc = otherBlockchainCbcSignedEvents;
        break;
      default:
        throw new RuntimeException("Unknown consensus methodology");
    }

    // Set-up client side and deploy contracts on the blockchains.
    otherBlockchain.deployContracts(otherBlockchainCbcContractAddress);
    BigInteger otherBcId = otherBlockchain.getBlockchainId();
    String otherBlockchainContractAddress = otherBlockchain.otherBlockchainContract.getContractAddress();

    rootBlockchain.deployContracts(rootBlockchainCbcContractAddress, otherBcId, otherBlockchainContractAddress);
    BigInteger rootBcId = rootBlockchain.getBlockchainId();

    // To make the example simple, just have one signer, and have the same signer for all blockchains.
    AnIdentity signer = new AnIdentity();
    otherBlockchainCbc.registerSignerThisBlockchain(signer);
    otherBlockchainCbc.registerSigner(signer, rootBcId);
    rootBlockchainCbc.registerSignerThisBlockchain(signer);
    rootBlockchainCbc.registerSigner(signer, otherBcId);

    // Create simulators
    SimOtherContract simOtherContract = new SimOtherContract();
    SimRootContract simRootContract = new SimRootContract(simOtherContract);

    // Do some single blockchain calls to set things up, to show that values have changed.
    // Ensure the simulator is set-up the same way.
    BigInteger otherBcValInitialValue = BigInteger.valueOf(77);
    simOtherContract.setVal(otherBcValInitialValue);
    otherBlockchain.setVal(otherBcValInitialValue);

    BigInteger rootBcVal1InitialValue = BigInteger.valueOf(78);
    simRootContract.setVal1(rootBcVal1InitialValue);
    rootBlockchain.setVal1(rootBcVal1InitialValue);
    BigInteger rootBcVal2InitialValue = BigInteger.valueOf(79);
    simRootContract.setVal2(rootBcVal2InitialValue);
    rootBlockchain.setVal2(rootBcVal2InitialValue);

    // Simulate passing in the parameter value 7 for the cross-blockchain call.
    BigInteger param = BigInteger.valueOf(7);
    simRootContract.someComplexBusinessLogic(param);

    String rlpFunctionCall_SomeComplexBusinessLogic = rootBlockchain.getRlpFunctionSignature_SomeComplexBusinessLogic(param);
    LOG.info("rlpFunctionCall_SomeComplexBusinessLogic: {}", rlpFunctionCall_SomeComplexBusinessLogic);
    String rlpFunctionCall_GetVal = otherBlockchain.getRlpFunctionSignature_GetVal();
    LOG.info("rlpFunctionCall_GetVal: {}", rlpFunctionCall_GetVal);
    String rlpFunctionCall_SetValues = null;
    String rlpFunctionCall_SetVal = null;
    if (simRootContract.someComplexBusinessLogicIfTrue) {
      rlpFunctionCall_SetValues = otherBlockchain.getRlpFunctionSignature_SetValues(
          simRootContract.someComplexBusinessLogicSetValuesParameter1,
          simRootContract.someComplexBusinessLogicSetValuesParameter2);
      LOG.info("rlpFunctionCall_SetValues: {}", rlpFunctionCall_SetValues);
    }
    else {
      rlpFunctionCall_SetVal = otherBlockchain.getRlpFunctionSignature_SetVal(simRootContract.someComplexBusinessLogicSetValParameter);
      LOG.info("rlpFunctionCall_SetVal: {}", rlpFunctionCall_SetVal);
    }

    RlpList callGraph;
    if (simRootContract.someComplexBusinessLogicIfTrue) {
      RlpList getVal = createLeafFunctionCall(otherBcId, otherBlockchainContractAddress, rlpFunctionCall_GetVal);
      RlpList setValues = createLeafFunctionCall(otherBcId, otherBlockchainContractAddress, rlpFunctionCall_SetValues);
      List<RlpType> calls = new ArrayList<>();
      calls.add(getVal);
      calls.add(setValues);
      callGraph = createRootFunctionCall(
          rootBcId, rootBlockchain.rootBlockchainContract.getContractAddress(), rlpFunctionCall_SomeComplexBusinessLogic, calls);
    }
    else {
      RlpList getVal = createLeafFunctionCall(otherBcId, otherBlockchainContractAddress, rlpFunctionCall_GetVal);
      RlpList setVal = createLeafFunctionCall(otherBcId, otherBlockchainContractAddress, rlpFunctionCall_SetVal);
      List<RlpType> calls = new ArrayList<>();
      calls.add(getVal);
      calls.add(setVal);
      callGraph = createRootFunctionCall(
          rootBcId, rootBlockchain.rootBlockchainContract.getContractAddress(), rlpFunctionCall_SomeComplexBusinessLogic, calls);
    }

    BigInteger crossBlockchainTransactionId1 = AbstractCbc.generateRandomCrossBlockchainTransactionId();
    BigInteger timeout = BigInteger.valueOf(300);

    LOG.info("start");
    boolean success;
    switch (consensusMethodology) {
      case TRANSACTION_RECEIPT_SIGNING:
        TransactionReceipt startTxReceipt = rootBlockchainCbcTxRootTransfer.start(crossBlockchainTransactionId1, timeout, RlpEncoder.encode(callGraph));
        TxReceiptRootTransferEventProof startProof = rootBlockchainCbcTxRootTransfer.getStartEventProof(startTxReceipt);

        // Add tx receipt root so event will be trusted.
        otherBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, rootBcId, startProof.getTransactionReceiptRoot());
        rootBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, rootBcId, startProof.getTransactionReceiptRoot());


        // Set of all segment proofs needed for the root call.
        List<TxReceiptRootTransferEventProof> allSegmentProofs = new ArrayList<>();
        // Set of all segments need for the signal call on Other Blockchain.
        List<TxReceiptRootTransferEventProof> signalSegProofs = new ArrayList<>();


        LOG.info("segment: getVal");
        StatsHolder.log("segment: getVal");
        List<BigInteger> getValCallPath = new ArrayList<>();
        getValCallPath.add(BigInteger.ONE);
        Tuple<TransactionReceipt, List<String>, Integer> result = otherBlockchainCbcTxRootTransfer.segment(startProof, new ArrayList<>(), getValCallPath);
        TransactionReceipt segGetValTxReceipt = result.getFirst();
        TxReceiptRootTransferEventProof segGetValProof = otherBlockchainCbcTxRootTransfer.getSegmentEventProof(segGetValTxReceipt);
        allSegmentProofs.add(segGetValProof);
        // Add tx receipt root so event will be trusted.
        otherBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, otherBcId, segGetValProof.getTransactionReceiptRoot());
        rootBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, otherBcId, segGetValProof.getTransactionReceiptRoot());

        if (simRootContract.someComplexBusinessLogicIfTrue) {
          LOG.info("segment: setValues");
          StatsHolder.log("segment: setValues");
          List<BigInteger> setValuesCallPath = new ArrayList<>();
          setValuesCallPath.add(BigInteger.TWO);
          result = otherBlockchainCbcTxRootTransfer.segment(startProof, new ArrayList<>(), setValuesCallPath);
          TransactionReceipt segSetValuesTxReceipt = result.getFirst();
          TxReceiptRootTransferEventProof segSetValuesProof = otherBlockchainCbcTxRootTransfer.getSegmentEventProof(segSetValuesTxReceipt);
          allSegmentProofs.add(segSetValuesProof);
          signalSegProofs.add(segSetValuesProof);
          // Add tx receipt root so event will be trusted.
          otherBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, otherBcId, segSetValuesProof.getTransactionReceiptRoot());
          rootBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, otherBcId, segSetValuesProof.getTransactionReceiptRoot());

        } else {
          LOG.info("segment: setVal");
          StatsHolder.log("segment: setVal");
          List<BigInteger> setValCallPath = new ArrayList<>();
          setValCallPath.add(BigInteger.TWO);
          result = otherBlockchainCbcTxRootTransfer.segment(startProof, new ArrayList<>(), setValCallPath);
          TransactionReceipt segSetValTxReceipt = result.getFirst();
          TxReceiptRootTransferEventProof segSetValProof = otherBlockchainCbcTxRootTransfer.getSegmentEventProof(segSetValTxReceipt);
          allSegmentProofs.add(segSetValProof);
          signalSegProofs.add(segSetValProof);
          // Add tx receipt root so event will be trusted.
          otherBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, otherBcId, segSetValProof.getTransactionReceiptRoot());
          rootBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, otherBcId, segSetValProof.getTransactionReceiptRoot());
        }

        LOG.info("root");
        TransactionReceipt rootTxReceipt = rootBlockchainCbcTxRootTransfer.root(startProof, allSegmentProofs);
        TxReceiptRootTransferEventProof rootProof = rootBlockchainCbcTxRootTransfer.getRootEventProof(rootTxReceipt);
        // Add tx receipt root so event will be trusted.
        otherBlockchainCbcTxRootTransfer.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, rootBcId, rootProof.getTransactionReceiptRoot());
//    rootBlockchain.addTransactionReceiptRootToBlockchain(new AnIdentity[]{signer}, rootBcId, rootProof.getTransactionReceiptRoot());

        LOG.info("signalling");
        // Do a signal call on all blockchain that have had segment calls that have caused contracts to be locked.
        otherBlockchainCbcTxRootTransfer.signalling(rootProof, signalSegProofs);

        success = rootBlockchainCbcTxRootTransfer.getRootEventSuccess();

        rootBlockchainCbcTxRootTransfer.shutdown();
        otherBlockchainCbcTxRootTransfer.shutdown();
        break;


      case EVENT_SIGNING:
        byte[] startEventData = rootBlockchainCbcSignedEvents.start(crossBlockchainTransactionId1, timeout, RlpEncoder.encode(callGraph));
        SignedEvent signedStartEvent = new SignedEvent(new AnIdentity[]{signer},
            rootBcId, rootBlockchainCbcContractAddress, AbstractCbc.START_EVENT_SIGNATURE, startEventData);

        // Set of all segment event information needed for the root call.
        List<SignedEvent> allSegmentEvents = new ArrayList<>();
        // Set of all segments need for the signal call on Other Blockchain.
        List<SignedEvent> signalSegEvents = new ArrayList<>();


        LOG.info("segment: getVal");
        StatsHolder.log("segment: getVal");
        getValCallPath = new ArrayList<>();
        getValCallPath.add(BigInteger.ONE);
        byte[] segEventData = otherBlockchainCbcSignedEvents.segment(signedStartEvent, getValCallPath);
        SignedEvent segGetValEvent = new SignedEvent(new AnIdentity[]{signer},
            otherBcId, otherBlockchainCbcContractAddress, AbstractCbc.SEGMENT_EVENT_SIGNATURE, segEventData);
        allSegmentEvents.add(segGetValEvent);

        if (simRootContract.someComplexBusinessLogicIfTrue) {
          LOG.info("segment: setValues");
          StatsHolder.log("segment: setValues");
          List<BigInteger> setValuesCallPath = new ArrayList<>();
          setValuesCallPath.add(BigInteger.TWO);
          segEventData = otherBlockchainCbcSignedEvents.segment(signedStartEvent, setValuesCallPath);
          SignedEvent segSetValuesEvent = new SignedEvent(new AnIdentity[]{signer},
              otherBcId, otherBlockchainCbcContractAddress, AbstractCbc.SEGMENT_EVENT_SIGNATURE, segEventData);
          allSegmentEvents.add(segSetValuesEvent);
          signalSegEvents.add(segSetValuesEvent);
        } else {

          LOG.info("segment: setVal");
          StatsHolder.log("segment: setVal");
          List<BigInteger> setValCallPath = new ArrayList<>();
          setValCallPath.add(BigInteger.TWO);
          segEventData = otherBlockchainCbcSignedEvents.segment(signedStartEvent, setValCallPath);
          SignedEvent segSetValEvent = new SignedEvent(new AnIdentity[]{signer},
              otherBcId, otherBlockchainCbcContractAddress, AbstractCbc.SEGMENT_EVENT_SIGNATURE, segEventData);
          allSegmentEvents.add(segSetValEvent);
          signalSegEvents.add(segSetValEvent);
        }

        LOG.info("root");
        byte[] rootEventData = rootBlockchainCbcSignedEvents.root(signedStartEvent, allSegmentEvents);
        SignedEvent rootEvent = new SignedEvent(new AnIdentity[]{signer},
            rootBcId, rootBlockchainCbcContractAddress, AbstractCbc.ROOT_EVENT_SIGNATURE, rootEventData);

        LOG.info("signalling");
        // Do a signal call on all blockchain that have had segment calls that have caused contracts to be locked.
        otherBlockchainCbcSignedEvents.signalling(rootEvent, signalSegEvents);

        success = rootBlockchainCbcSignedEvents.getRootEventSuccess();

        rootBlockchainCbcSignedEvents.shutdown();
        otherBlockchainCbcSignedEvents.shutdown();
        break;

      default:
        throw new RuntimeException("Unknown consensus type");
    }


    LOG.info("Cross-Blockchain Transaction was successful: {}", success);
    if (success) {
      LOG.info(" Simulated expected values: Root val1: {}, val2: {}, Other val: {}",
          simRootContract.getVal1(), simRootContract.getVal2(), simOtherContract.getVal());
    }
    else {
      LOG.info(" Expect unchanged initial values: Root val1: {}, val2: {}, Other val: {}",
          rootBcVal1InitialValue, rootBcVal2InitialValue, otherBcValInitialValue);
    }
    rootBlockchain.showValues();
    otherBlockchain.showValues();
    LOG.info(" Other contract's storage is locked: {}", otherBlockchain.storageIsLocked());

    rootBlockchain.shutdown();
    otherBlockchain.shutdown();

    StatsHolder.log("End");
    StatsHolder.print();
  }
}
