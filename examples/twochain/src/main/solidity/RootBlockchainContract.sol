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
pragma solidity >=0.7.1;

import "../../../../../crossblockchaincontrol/src/main/solidity/CrossBlockchainControl.sol";
import "./OtherBlockchainContractInterface.sol";
import "../../../../../lockablestorage/src/main/solidity/LockableStorageWrapper.sol";

contract RootBlockchainContract is LockableStorageWrapper {
    CrossBlockchainControl private crossBlockchainControlContract;
    uint256 private otherBlockchainId;
    OtherBlockchainContractInterface private otherContract;

    uint256 private KEY_LOCAL_UINT;

    constructor (address _crossBlockchainControl, uint256 _otherBlockchainId, address _otherContract, address _storageContract)
        LockableStorageWrapper(_storageContract) {
        crossBlockchainControlContract = CrossBlockchainControl(_crossBlockchainControl);
        otherBlockchainId = _otherBlockchainId;
        otherContract = OtherBlockchainContractInterface(_otherContract);
    }

    function someComplexBusinessLogic(uint256 _val) external {
        // Use the value on the other blockchain as a threshold
        uint256 currentThreshold = crossBlockchainControlContract.crossBlockchainCallReturnsUint256(
            otherBlockchainId, address(otherContract), abi.encodeWithSelector(otherContract.getVal.selector));

        if (_val > currentThreshold) {
            crossBlockchainControlContract.crossBlockchainCall(otherBlockchainId, address(otherContract),
                abi.encodeWithSelector(otherContract.setValues.selector, _val, currentThreshold));
            setLocalVal(currentThreshold);
        }
        else {
            setValRemote(1);
            setLocalVal(_val);
        }
    }

    function setValRemote(uint256 _val) public {
        crossBlockchainControlContract.crossBlockchainCall(otherBlockchainId, address(otherContract),
            abi.encodeWithSelector(otherContract.setVal.selector, _val));
    }

    function getRemoteVal() external {
        uint256 val = crossBlockchainControlContract.crossBlockchainCallReturnsUint256(otherBlockchainId, address(otherContract),
            abi.encodeWithSelector(otherContract.getVal.selector));
        setUint256(KEY_LOCAL_UINT, val);
    }

    function setLocalVal(uint256 _val) public {
        setUint256(KEY_LOCAL_UINT, _val);
    }

    function getLocalVal() external view returns (uint256) {
        return getUint256(KEY_LOCAL_UINT);
    }
}
