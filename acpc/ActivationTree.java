/*
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR 
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER 
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package acpc;

import java.util.ArrayList;
import java.util.Collections;

/**
 * An abstraction for an ACPC activation tree. It stores the list of revoked
 * nodes and allows non-revoked nodes to be picked at different levels of the
 * tree, counting how many nodes are still available (i.e., are not revoked and
 * can still be picked) after each of such operations. Its nodes are represented
 * starting at 1. Hence, representing its node IDs in binary, the root gets the
 * value 1, its left and right children are 10 and 11, their respective children
 * are 100, 101, 110 and 111, and so forth. The leaves, corresponding to vehicle
 * IDs with n bits, are then represented with values going from 10<sup>n</sup>
 * (for the leftmost leaf) through 11<sup>n</sup> (for the rightmost leaf).
 * Therefore, when the vehicle's identifier is n-bits long, the height of the
 * tree is n+1.
 * <pre>
 *                         0001                          => depth 0
 *                ________/    \________
 *               /                       \
 *           0010                        0011            => depth 1
 *        __/    \__                  __/    \__
 *       /          \                /          \
 *    0100          0101          0110          0111     => depth 2
 *    /  \          /  \          /  \          /  \
 * 1000  1001    1010  1011    1100  1101    1110  1111  => depth 3
 * </pre>
 */
public class ActivationTree {

    /**
     * The list of IDs for revoked nodes at each depth of the tree
     */
    private final ArrayList<Long>[/*treeDepth = idLength+1*/] revokedNodes;

    /**
     * The number of non-revoked that can been picked at each depth of the tree
     */
    private final long[/*treeDepth = idLength+1*/] pickeableNodesCounter;

    /**
     * Constructor for an Activation Tree supporting 2<sup>idLength</sup>
     * vehicles. The tree's actual height will be idLength+1.
     *
     * @param idLength The bit-length that defines the number of vehicles
     * supported by the activation tree.
     */
    public ActivationTree(int idLength) {
        //Instantiates the arrays
        this.revokedNodes = new ArrayList[idLength + 1];
        this.pickeableNodesCounter = new long[idLength + 1];

        /*Initializes revoked nodes (none) and pickable nodes
        (only the root is pickeable when there are no revocations)*/
        for (int i = 0; i < this.revokedNodes.length; i++) {
            this.revokedNodes[i] = new ArrayList<>();
            this.pickeableNodesCounter[i] = (i == 0 ? 1 : 0);
        }
    }

    /**
     * Revoke a node, marking its whole path to the root as revoked. Due to
     * internal optimizations, this method only works correctly if the nodeId
     * passed as parameter is larger than any previously revoked nodeId.
     *
     * @param nodeId The ID of the node to be revoked (it must be larger than
     * any previously revoked ID).
     */
    private void revoke(long nodeId) {
        //Used to get the bits we need at each depth of the tree
        long shiftLenght = revokedNodes.length - 1;

        //Revoke all ancestors of nodeId, starting from the root
        for (int depth = 0; depth < revokedNodes.length; depth++) {
            //Gets the list of revoked nodes from "depth"
            ArrayList<Long> nodesAtDepth = revokedNodes[depth];
            //Gets the index for the last node listed in this array list: assuming
            //that the values of nodeId are passed to this method in ascending order,
            //this node should have the highest ID at this depth. 
            int lastIndex = nodesAtDepth.size() - 1;

            //Gets all prefixes of nodeId: those are the ancestors of that leaf
            long id = nodeId >>> shiftLenght;

            //Check if the the nodeId given as parameter is not already revoked 
            //at this depth: it will be the case if it is smaller than the ID
            //at lastIndex (this is an optimization that only works if this method
            //is called in such a manner that each nodeId is passed in ascending order
            if (lastIndex == -1 || nodesAtDepth.get(lastIndex) != id) {
                //This is a new node to be revoked
                nodesAtDepth.add(id);

                {// ============= UPDATING PICKEABLE NODES ==============
                    //There is one less node pickeable at this depth
                    pickeableNodesCounter[depth]--;
                    //This makes the two nodes below pickeable (obs.: except for leaves,
                    //the next iteration of the loop eliminates one of them)
                    if (depth + 1 < pickeableNodesCounter.length) {
                        pickeableNodesCounter[depth + 1] += 2;
                    }
                }
            }
            //Go to the next prefix (a child of node whose identifier was 
            //given by variable "id")
            shiftLenght--;
        }
    }

    /**
     * Revoke a list of nodes. The list does not need to be ordered.
     *
     * @param nodeIds The list of IDs to be revoked
     */
    public void revoke(ArrayList<Long> nodeIds) {
        //Sorts the list, because it allows an optimized process for 
        //adding individual nodes into the tree (see method 
        //"void revoke(long nodeId)")
        Collections.sort(nodeIds);

        //Revokes each individual nodeId in this (now sorted) list
        for (Long id : nodeIds) {
            this.revoke(id);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        //Createas a human-friendly (or at least not too unfriendly...) 
        //representation for the tree
        for (ArrayList<Long> nodesAtDepth : revokedNodes) {
            sb.append(nodesAtDepth.size());
            sb.append(":\t[ ");
            for (Long nodeId : nodesAtDepth) {
                sb.append(Long.toBinaryString(nodeId));
                sb.append(" ");
            }

            sb.append("]\n");
        }

        return sb.toString();
    }

    /**
     * Counts the number of nodes available (i.e., not revoked and not picked)
     * at the given depth of the tree, going from the root (depth = 0) to the
     * leaves (depth = idLength + 1)
     *
     * @param depth The depth where the counting should be performed.
     * @return The number of nodes available (i.e., not revoked) at the depth
     * passed as parameter
     */
    public long countAvailableNodes(int depth) {
        return this.pickeableNodesCounter[depth];
    }

    /**
     * Marks a node as "picked", meaning that all nodes below it (i.e., its
     * descendants) should not be picked once again. This is used basically to
     * know how many nodes can still be picked at a given depth, when using the
     * {@link #countAvailableNodes(int depth)} method
     *
     * @param depth The depth from which a node must be picked
     *
     * @return The number of nodes that are still available at depth, after this
     * node is picked.
     */
    public long markPickedNode(int depth) {
        //A node at this depth was picked: there is one less pickeable node here
        this.pickeableNodesCounter[depth]--;

        return this.countAvailableNodes(depth);
    }

    /**
     * Counts how many leaves exist below any node from the baseline depth
     * passed as parameter. This is useful to determine the privacy gained when
     * picking a node from this depth (which we call "crowd size").
     *
     * @param baselineDepth The reference depth, for which to count the number
     * of descendant leaves.
     * @return The number of leaves descendant from any node at depth
     * baselineDepth
     */
    public long countDescendantLeaves(int baselineDepth) {
        int targetDepth = this.revokedNodes.length - 1; //last level

        //Note: this is an optimization of Math.pow(2, (targetDepth - baselineDepth));
        return (long) (1l << (targetDepth - baselineDepth));
    }

    /**
     * Counts how many nodes at depth targetDepth are descendants of a node at
     * depth baselineDepth. This is useful to count the number of nodes at any
     * depth targetDepth that would not need to be picked because there is a
     * higher up node at depth baselineDepth that, if picked, will already cover
     * those nodes
     *
     * @param baselineDepth The higher depth
     * @param targetDepth The lower depth
     *
     * @return The number of nodes at depth targetDepth that are descendants of
     * a node at depth baselineDepth
     */
    public long countMaxNodesBelow(int baselineDepth, int targetDepth) {
        if (baselineDepth > targetDepth) {
            return (long) 0;
        }
        //Note: this is an optimization of Math.pow(2, (targetDepth - baselineDepth));
        return (long) (1l << (targetDepth - baselineDepth));
    }

    /**
     * Gives the total number of pickable nodes from the tree (this would be the
     * number of nodes included in a broadcast)
     *
     * @return The number of nodes that are required to allow the computation of
     * any leaf from the activation tree
     */
    public long countAllPickableNodes() {
        long total = 0;

        //Adds up all pickable nodes from each depth
        for (long n : pickeableNodesCounter) {
            total += n;
        }

        return total;
    }

    /**
     * Just a small code snipet for testing
     *
     * @param args
     */
    public static void main(String[] args) {

        //Create a tree for a small id lenght, so the tree can be drawn
        int idLen = 4;
        ActivationTree t = new ActivationTree(idLen);
        //                                                 (Height is 5)
        //                                                     0001                                                         => depth 0
        //                            ________________________/    \_______________________
        //                           /                                                     \
        //                        0010                                                    0011                              => depth 1
        //               ________/    \________                                  ________/    \________ 
        //              /                      \                                /                      \
        //          0100                        0101                         0110                         0111              => depth 2
        //       __/    \__                  __/    \__                   __/    \__                   __/    \__
        //      /          \                /          \                 /          \                 /          \
        //   1000           1001         1010          1011           1100           1101          1110          1111       => depth 3
        //   /  \           /  \         /  \          /  \           /  \           /  \          /  \          /  \
        //10000 10001   10010 10011   10100 10101   10110 10111    11000 11001   11010 11011   11100 11101   11110 11111    => depth 4
        //  0     1       2     3       4     5       6     7        8     9       10   11       12   13       14   15      (ids)
        //Adds some nodes to a revocation list
        ArrayList<Long> revocationList = new ArrayList<>();
        
        revocationList.add(Long.valueOf(0b10000));
        revocationList.add(Long.valueOf(0b10001));
        revocationList.add(Long.valueOf(0b11111));
        revocationList.add(Long.valueOf(0b11110));
        revocationList.add(Long.valueOf(0b11101));

        
        
        //Revoke the selected nodes and prints the resulting activation tree
        t.revoke(revocationList);
        System.out.println(t);

        //Prints some numbers applicable to this activation tree
        for (int depth = 0; depth < t.revokedNodes.length; depth++) {
            System.out.print("Available at depth " + depth + ": " + t.countAvailableNodes(depth)
                    + " (pickeable: " + t.pickeableNodesCounter[depth] + ")");
            System.out.println(". Crowd size for such a node: " + t.countDescendantLeaves(depth));
        }
    }

}
