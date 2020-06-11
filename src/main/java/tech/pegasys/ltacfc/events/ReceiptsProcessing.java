package tech.pegasys.ltacfc.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.SimpleMerklePatriciaTrie;
import tech.pegasys.ltacfc.Main;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReceiptsProcessing {
  private static final Logger LOG = LogManager.getLogger(ReceiptsProcessing.class);


  /**
   * Generates the receipt root for a list of receipts
   *
   * @param receipts the receipts
   * @return the receipt root
   */
  public static Hash receiptsRoot(final List<TransactionReceipt> receipts) {
    final MerklePatriciaTrie<Bytes, Bytes> trie = trie();

    for (int i = 0; i < receipts.size(); ++i) {
      trie.put(indexKey(i), RLP.encode(receipts.get(i)::writeTo));
    }

    return Hash.wrap(trie.getRootHash());
  }

  public static void logTrie(final List<TransactionReceipt> receipts) {
    LOG.info("Receipts Trie");
    final MerklePatriciaTrie<Bytes, Bytes> trie = trie();

    for (int i = 0; i < receipts.size(); ++i) {
      trie.put(indexKey(i), RLP.encode(receipts.get(i)::writeTo));
    }

    Bytes key1 = indexKey(0);
    LOG.info("key1 = {}", key1);
//    Bytes32 key2 = Bytes32.rightPad(key1);
//    LOG.info("key2: {}", key2);
    Optional<Bytes> value = trie.get(key1);
    LOG.info("Value: {}", value.toString());

    Proof<Bytes> proofAndValue = trie.getValueWithProof(key1);
    LOG.info("Proof and value: {}, num related nodes: {}", proofAndValue.getValue(), proofAndValue.getProofRelatedNodes().size());
    LOG.info("related node: {}", proofAndValue.getProofRelatedNodes().get(0));


//    Map<Bytes32, Bytes> entries = trie.entriesFrom(key2, 1000);
//    for (Bytes32 key: entries.keySet()) {
//      Bytes value = entries.get(key);
//      LOG.info(" Tree entry: key: {}, value: {}", key, value);
//    }

  }

  private static MerklePatriciaTrie<Bytes, Bytes> trie() {
    return new SimpleMerklePatriciaTrie<>(b -> b);
  }

  private static Bytes indexKey(final int i) {
    return RLP.encodeOne(UInt256.valueOf(i).toBytes().trimLeadingZeros());
  }

}
