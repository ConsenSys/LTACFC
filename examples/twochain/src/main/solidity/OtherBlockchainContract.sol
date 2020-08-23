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
pragma solidity >=0.6.9;
import "./OtherBlockchainContractInterface.sol";

contract OtherBlockchainContract is OtherBlockchainContractInterface {

    uint256 val;
    bool flag;
    uint256[] vals;

    function setVal(uint256 _val) external override {
        val = _val;
    }

    function incrementVal() external override {
        val++;
    }

    function getVal() external override view returns(uint256) {
        return val;
    }

    function setFlag(bool _flag) external override {
        flag = _flag;
    }

    function getFlag() external override view returns(bool) {
        return flag;
    }

    function setValAndFlag(bool _flag, uint256 _val) external override {
        flag = _flag;
        val = _val;
    }

    function getValAndFlag() external override view returns(bool, uint256) {
        return (flag, val);
    }

    function setValues(uint256[] calldata _vals) external override {
        vals = _vals;
    }

    function getValue(uint256 _index) external override view returns(uint256) {
        return vals[_index];
    }

}
