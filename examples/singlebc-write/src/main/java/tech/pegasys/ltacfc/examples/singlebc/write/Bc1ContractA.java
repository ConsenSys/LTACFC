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
package tech.pegasys.ltacfc.examples.singlebc.write;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.ltacfc.cbc.AbstractBlockchain;
import tech.pegasys.ltacfc.common.StatsHolder;
import tech.pegasys.ltacfc.examples.singlebc.write.soliditywrappers.ContractA;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;


public class Bc1ContractA extends AbstractBlockchain {
  private static final Logger LOG = LogManager.getLogger(Bc1ContractA.class);

  ContractA contractA;

  public Bc1ContractA(Credentials credentials, String bcId, String uri, String gasPriceStrategy, String blockPeriod) throws IOException {
    super(credentials, bcId, uri, gasPriceStrategy, blockPeriod);
  }

  public void deployContracts(String busLogicContractAddress) throws Exception {
    this.contractA =
        ContractA.deploy(this.web3j, this.tm, this.gasProvider, busLogicContractAddress).send();
    LOG.info("ContractA deployed to {} on blockchain 0x{}",
        this.contractA.getContractAddress(), this.blockchainId.toString(16));
  }

  public TransactionReceipt doWrite(BigInteger val) throws Exception {
    TransactionReceipt txR = this.contractA.doWrite(val).send();
    StatsHolder.logGas("doWrite", txR.getGasUsed());
    return txR;
  }
}
