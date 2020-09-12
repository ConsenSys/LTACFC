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
package tech.pegasys.ltacfc.lockablestorage.test;

import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.exceptions.ContractCallException;
import tech.pegasys.ltacfc.lockablestorage.soliditywrappers.LockableStorage;
import tech.pegasys.ltacfc.lockablestorage.soliditywrappers.LockableStorageWrapper;
import tech.pegasys.ltacfc.lockablestorage.soliditywrappers.MockCbcForLockableStorageTest;
import tech.pegasys.ltacfc.test.AbstractWeb3Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

/**
 * Check operation, assuming the calls are single blockchain (that is not part of a
 * cross-blockchain) call.
 */
public class LockableStorageSingleBlockchainTest extends AbstractWeb3Test {
  LockableStorageWrapper storageWrapper;
  LockableStorage lockableStorageContract;
  MockCbcForLockableStorageTest mockCrossBlockchainControlContract;




  protected void deployContracts() throws Exception {
    this.mockCrossBlockchainControlContract = MockCbcForLockableStorageTest.deploy(this.web3j, this.tm, this.freeGasProvider).send();
    this.lockableStorageContract = LockableStorage.deploy(this.web3j, this.tm, this.freeGasProvider,
        this.mockCrossBlockchainControlContract.getContractAddress()).send();
    this.storageWrapper = LockableStorageWrapper.deploy(this.web3j, this.tm, this.freeGasProvider,
        this.lockableStorageContract.getContractAddress()).send();
    this.lockableStorageContract.setBusinessLogicContract(this.storageWrapper.getContractAddress()).send();
  }


  @Test
  public void checkDeployment() throws Exception {
    setupWeb3();
    deployContracts();

    assert(!this.lockableStorageContract.locked().send());
    assertEquals(this.lockableStorageContract.businessLogicContract().send(), this.storageWrapper.getContractAddress());
  }


  @Test
  public void bool() throws Exception {
    setupWeb3();
    deployContracts();

    BigInteger keyTestBool = BigInteger.ZERO;

    // Check the default value.
    assert (!this.storageWrapper.getBool(keyTestBool).send());

    // Check the value after setting to true
    try {
      this.storageWrapper.setBool(keyTestBool, true).send();
    } catch (TransactionException ex) {
      if (ex.getTransactionReceipt().isPresent()) {
        TransactionReceipt receipt = ex.getTransactionReceipt().get();
        System.out.println("Revert Reason: " + receipt.getRevertReason());
      }
      throw ex;
    }
    assert(this.storageWrapper.getBool(keyTestBool).send());

      // Check the value after setting to false
      try {
        this.storageWrapper.setBool(keyTestBool, false).send();
      } catch (TransactionException ex) {
        if (ex.getTransactionReceipt().isPresent()) {
          TransactionReceipt receipt = ex.getTransactionReceipt().get();
          System.out.println("Revert Reason: " + receipt.getRevertReason());
        }
        throw ex;
      }
      assert(!this.storageWrapper.getBool(keyTestBool).send());

  }


  @Test
  public void uint256() throws Exception {
    setupWeb3();
    deployContracts();

    BigInteger uint256A = BigInteger.ZERO;
    BigInteger uint256B = BigInteger.ONE;
    BigInteger val1 = BigInteger.valueOf(0x1234567);
    BigInteger val2 = BigInteger.valueOf(33);
    BigInteger val3 = BigInteger.valueOf(76543);

    // Check the default value.
    assert(this.storageWrapper.getUint256(uint256A).send().compareTo(BigInteger.ZERO) == 0);

    // Set the first value
    try {
      this.storageWrapper.setUint256(uint256A, val1).send();
    } catch (TransactionException ex) {
      if (ex.getTransactionReceipt().isPresent()) {
        TransactionReceipt receipt = ex.getTransactionReceipt().get();
        System.out.println("Revert Reason: " + receipt.getRevertReason());
      }
      throw ex;
    }
    assert(this.storageWrapper.getUint256(uint256A).send().compareTo(val1) == 0);

    // Set the second value
    try {
      this.storageWrapper.setUint256(uint256B, val2).send();
    } catch (TransactionException ex) {
      if (ex.getTransactionReceipt().isPresent()) {
        TransactionReceipt receipt = ex.getTransactionReceipt().get();
        System.out.println("Revert Reason: " + receipt.getRevertReason());
      }
      throw ex;
    }
    assert(this.storageWrapper.getUint256(uint256B).send().compareTo(val2) == 0);
    // Make sure uint256A hasn't been changed.
    assert(this.storageWrapper.getUint256(uint256A).send().compareTo(val1) == 0);

    // Check that an existing value can be changed
    try {
      this.storageWrapper.setUint256(uint256A, val3).send();
    } catch (TransactionException ex) {
      if (ex.getTransactionReceipt().isPresent()) {
        TransactionReceipt receipt = ex.getTransactionReceipt().get();
        System.out.println("Revert Reason: " + receipt.getRevertReason());
      }
      throw ex;
    }
    assert(this.storageWrapper.getUint256(uint256A).send().compareTo(val3) == 0);

  }

  @Test
  public void array() throws Exception {
    setupWeb3();
    deployContracts();

    BigInteger arrayA = BigInteger.ZERO;
    BigInteger arrayB = BigInteger.ONE;
    BigInteger val1 = BigInteger.valueOf(0x1234567);
    BigInteger val2 = BigInteger.valueOf(33);
    BigInteger val3 = BigInteger.valueOf(76543);

    // Check the default length.
    assert(this.storageWrapper.getArrayLength(arrayA).send().compareTo(BigInteger.ZERO) == 0);
    assert(this.storageWrapper.getArrayLength(arrayB).send().compareTo(BigInteger.ZERO) == 0);

    // Trying pop on empty array.
    try {
      this.storageWrapper.popArrayValue(arrayA).send();
      throw new Exception("Pop on zero length array did not cause an exception");
    } catch (TransactionException ex) {
      if (ex.getTransactionReceipt().isPresent()) {
        TransactionReceipt receipt = ex.getTransactionReceipt().get();
        System.out.println("Revert Reason: " + receipt.getRevertReason());
      }
    }

    // Push value onto the end of the array.
    this.storageWrapper.pushArrayValue(arrayA, val1).send();
    assert(this.storageWrapper.getArrayLength(arrayA).send().compareTo(BigInteger.ONE) == 0);
    assert(this.storageWrapper.getArrayLength(arrayB).send().compareTo(BigInteger.ZERO) == 0);
    assert(this.storageWrapper.getArrayValue(arrayA, BigInteger.ZERO).send().compareTo(val1) == 0);

    // Push value onto the end of the array.
    this.storageWrapper.pushArrayValue(arrayA, val2).send();
    assert(this.storageWrapper.getArrayLength(arrayA).send().compareTo(BigInteger.TWO) == 0);
    assert(this.storageWrapper.getArrayLength(arrayB).send().compareTo(BigInteger.ZERO) == 0);
    assert(this.storageWrapper.getArrayValue(arrayA, BigInteger.ZERO).send().compareTo(val1) == 0);
    assert(this.storageWrapper.getArrayValue(arrayA, BigInteger.ONE).send().compareTo(val2) == 0);

    // Push value onto the end of the array.
    this.storageWrapper.pushArrayValue(arrayB, val3).send();
    assert(this.storageWrapper.getArrayLength(arrayA).send().compareTo(BigInteger.TWO) == 0);
    assert(this.storageWrapper.getArrayLength(arrayB).send().compareTo(BigInteger.ONE) == 0);
    assert(this.storageWrapper.getArrayValue(arrayB, BigInteger.ZERO).send().compareTo(val3) == 0);
    assert(this.storageWrapper.getArrayValue(arrayA, BigInteger.ZERO).send().compareTo(val1) == 0);

    // Trying getting a value that is out of bounds.
    try {
      this.storageWrapper.getArrayValue(arrayA, BigInteger.TEN).send();
      throw new Exception("Get for array out of bounds did not cause an exception");
    } catch (ContractCallException ex) {
      // Thrown as expected: empty value returned
    }

    // Pop all of the values.
    this.storageWrapper.popArrayValue(arrayA).send();
    this.storageWrapper.popArrayValue(arrayA).send();
    this.storageWrapper.popArrayValue(arrayB).send();
    assert(this.storageWrapper.getArrayLength(arrayA).send().compareTo(BigInteger.ZERO) == 0);
    assert(this.storageWrapper.getArrayLength(arrayB).send().compareTo(BigInteger.ZERO) == 0);

  }


  @Test
  public void map() throws Exception {
    setupWeb3();
    deployContracts();

    BigInteger mapA = BigInteger.ZERO;
    BigInteger mapB = BigInteger.ONE;
    BigInteger key1 = BigInteger.TEN;
    BigInteger key2 = BigInteger.TWO;

    BigInteger val1 = BigInteger.valueOf(0x1234567);
    BigInteger val2 = BigInteger.valueOf(33);
    BigInteger val3 = BigInteger.valueOf(76543);
    BigInteger val4 = BigInteger.valueOf(123);

    // Add two values to each map for the same keys. Check that they are stored uniquely.
    this.storageWrapper.setMapValue(mapA, key1, val1).send();
    this.storageWrapper.setMapValue(mapA, key2, val2).send();
    this.storageWrapper.setMapValue(mapB, key1, val3).send();
    this.storageWrapper.setMapValue(mapB, key2, val4).send();

    assert(this.storageWrapper.getMapValue(mapA, key1).send().compareTo(val1) == 0);
    assert(this.storageWrapper.getMapValue(mapA, key2).send().compareTo(val2) == 0);
    assert(this.storageWrapper.getMapValue(mapB, key1).send().compareTo(val3) == 0);
    assert(this.storageWrapper.getMapValue(mapB, key2).send().compareTo(val4) == 0);
  }


  @Test
  public void address() throws Exception {
    setupWeb3();
    deployContracts();

    BigInteger addressA = BigInteger.ZERO;
    BigInteger addressB = BigInteger.ONE;

    String addressVal1 = this.lockableStorageContract.getContractAddress();
    String addressVal2 = this.mockCrossBlockchainControlContract.getContractAddress();

    this.storageWrapper.setAddress(addressA, addressVal1).send();
    this.storageWrapper.setAddress(addressB, addressVal2).send();

    assert(this.storageWrapper.getAddress(addressA).send().equalsIgnoreCase(addressVal1));
    assert(this.storageWrapper.getAddress(addressB).send().equalsIgnoreCase(addressVal2));
  }

  @Test
  public void bytes() throws Exception {
    setupWeb3();
    deployContracts();

    BigInteger bytesA = BigInteger.ZERO;
    BigInteger bytesB = BigInteger.ONE;

    byte[] val1 = new byte[]{10, 11, 12, 13};
    byte[] val2 = new byte[]{7};

    this.storageWrapper.setBytes(bytesA, val1).send();
    this.storageWrapper.setBytes(bytesB, val2).send();

    byte[] result1 = this.storageWrapper.getBytes(bytesA).send();
    byte[] result2 = this.storageWrapper.getBytes(bytesB).send();

    assertEquals(val1.length, result1.length);
    assertEquals(val2.length, result2.length);
    assertEquals(val1[0], result1[0]);
    assertEquals(val1[1], result1[1]);
    assertEquals(val1[2], result1[2]);
    assertEquals(val2[0], result2[0]);
  }

}

