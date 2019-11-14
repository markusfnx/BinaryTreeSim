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

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/**
 * A simulator for evaluating the Fixed-Size Subset (FSS) and Variable-Size
 * Subset (VSS) picking strategies for different revocation scenarios. For FSS,
 * the simulator allows the computation of the crowd size obtained when picking
 * only log(n) nodes from an ACPC activation tree, where the leaf IDs are n-bit
 * long. For VSS, we set the desired privacy level and compute how many nodes
 * need to be picked from the activation tree for attaining it.
 */
public class Simulator {

    /**
     * Length of vehicle IDs as defined in ACPC and BCAM: 40 bits, so
     * approximately 1 trillion vehicles are supported
     */
    public static final int DEFAULT_VID_LENGTH = 40;

    /**
     * Plank's constant first 9 digits: used as source of pseudorandomness
     */
    public static final long PLANK_CONSTANT = 662607004;

    /**
     * Pi's first 6 digits: used as source of pseudorandomness
     */
    public static final long PI_CONSTANT = 314159;

    /**
     * Length of vehicle IDs to be used in the simulation.
     *
     * @see #DEFAULT_VID_LENGTH
     */
    private final int idLength;

    /**
     * Height of the activation tree. Since each leaf is associated with one
     * vehicle and the root starts at 1, the height is computed as
     * ({@link #idLength} + 1)
     */
    private final int treeHeight;

    /**
     * Instantiates the Simulator with the desired idLenght. The activation
     * tree's height will then be idLength+1.
     *
     * @param idLength The length of the IDs to be simulated.
     *
     * @see ActivationTree
     */
    public Simulator(int idLength) {
        this.idLength = idLength;
        this.treeHeight = idLength + 1;
    }

    /**
     * Checks if the number of revocations passed as parameter is indeed
     * supported by the simulator: it must be smaller than the total number of
     * leaves in the activation tree.
     *
     * @param numberRevocations The number of revocations to be checked
     * @return true if the simulator has enough leaves to support the number of
     * revocations passed as parameter
     */
    private boolean isSupported(int numberRevocations) {
        //Equivalent to (numberRevocations <= Math.pow(2, this.idLength));
        return (numberRevocations <= (1l << this.idLength));
    }

    /**
     * Generates a list of long values that results in the best possible case
     * when there are revocations: each revoked leaf is as close as possible to
     * previously revoked leaves. As a result, the list of revoked nodes creates
     * paths that cover as few internal nodes at the lowest depths as possible.
     * <br><br>
     * In this implementation, this is obtained starting with the left-most
     * leaf, whose ID is 10<sup>idlenght</sup>
     * (i.e., a bit '1' followed by {@link #idLength} 0 bits), and incrementing
     * the ID until numberRevocations are obtained. All those IDs are placed in
     * the list given as this method's response.
     * <br><br>
     * Example: assume a tree where the IDs are 4-bit long, so its 16 leaves (at
     * depth 4) have identifiers going from 10000 to 11111; for 2 revocations,
     * this method ensures that only internal node 10 (at depth 1) is in the
     * revocation path (namely, by revoking 10000 and 10001); when 7 revocations
     * occur, the nodes chosen are (10000 to 10111), so the sibling of node 10
     * at depth 1 (i.e., node 11) is still not part of the revocation path.
     *
     * @param numberRevocations The number of leaf IDs to be generated.
     * @return The list of long values that represents the best case in terms of
     * revocations, i.e., the case where fewer nodes at lower depths are part of
     * some revocation path.
     */
    public ArrayList<Long> genBestCase(int numberRevocations) {

        //Check if the number of revocations requested is valid
        if (!isSupported(numberRevocations)) {
            throw new IllegalArgumentException("Cannot generate " + numberRevocations
                    + " values for an ID length of " + this.idLength);
        }

        //Generates sequence of IDs to be revoked
        ArrayList<Long> list = new ArrayList<>(numberRevocations);

        //Places the leftmost 1 (to indicate a leaf) at the correct position
        long baseId = (1l << this.idLength);
        for (long i = 0; i < numberRevocations; i++) {
            //Creates sequencial IDs
            long id = baseId | i;
            list.add(id);
        }

        return list;
    }

    /**
     * Generates a list of long values that results in the worst possible case
     * when there are revocations: is ensured to include the internal node at
     * the lowest depth when considering previously revoked leaves. As a result,
     * the list of revoked nodes creates paths that cover as many internal nodes
     * at the lowest depths as possible.
     * <br><br>
     * Example: assume a tree where the IDs are 4-bit long, so its leaves (at
     * depth 4) have identifiers going from 10000 to 11111; for 2 revocations,
     * this method ensures that internal nodes 10 and 11 (all nodes at depth 1)
     * are in the revocation path which is obtained by revoking 10000 and 11000;
     * for 4 revocations, this method ensures that internal nodes 100, 101, 110
     * and 111 (at depth 2) are in the revocation path (namely, by revoking
     * 10000, 10100, 11000, and 11100); and so forth.
     *
     *
     * @param numberRevocations The number of leaf IDs to be generated.
     * @return The list of long values that represents the worst case in terms
     * of revocations, i.e., the case where most nodes at lower depths are part
     * of some revocation path.
     */
    public ArrayList<Long> genWorstCase(int numberRevocations) {
        //Check if the number of revocations requested is valid
        if (!isSupported(numberRevocations)) {
            throw new IllegalArgumentException("Cannot generate " + numberRevocations
                    + " values for an ID length of " + this.idLength);
        }

        //Generates sequence of IDs to be revoked
        ArrayList<Long> list = new ArrayList<>();

        //Places the leftmost 1 (to indicate a leaf) at the correct position
        long baseId = (1l << this.idLength);

        /**
         * ***************** HOW NODE IDS ARE PICKED ****************** The
         * number of depths that can be completely depleted of pickable nodes
         * corresponds to the largest power of two smaller than
         * numberRevocations, denoted "largestPowerOf2". For example, with
         * numberRevocations = 2 or 3, we start by revoking leaves 10000 and
         * 11000 aiming to eliminate all internal nodes from depth 1 (namely, 10
         * and 11); the IDs of those leaves are computed simply as 1 (to
         * indicate that it is a leaf) followed by (0, 1) padded with zeros to
         * the right. With numberRevocations = 4 to 7, we start by revoking
         * (10000, 10100, 11000, 11100), thus eliminating all internal nodes
         * from depth 2 (namely, 100, 101, 110 and 111); these IDs are computed
         * as 1 (to indicate a leaf) followed by (00,01,10,11) padded with zeros
         * to the right. More generally, revoked IDs are computed as
         * 1||counter||0, where counter goes from 0 to largestPowerOf2-1.
         *
         * The remaining (numberRevocations-largestPowerOf2) leaves to be
         * revoked do not deplete an entire depth, but reduce the number of
         * available nodes at the shallowest depth. Hence, for numberRevocations
         * = 3, the remaining 1 leaf picked is the 10100, which eliminates one
         * extra internal node from depth 2: node 101, in addition to 100
         * (already removed due to the revocation of 10000) and 110 (removed by
         * revoking 11000). For numberRevocations = 7, the remaining 3 leaves
         * picked are (10010, 10100, 10110), which remove three extra internal
         * nodes from depth 3: 1001, 1011 and 1101 join their siblings 1000,
         * 1010, and 1100, leaving only 1111 as a pickable node at depth 3.
         * *******************************************************************
         */
        //Get the largest power of 2 smaller than numberRevocations.
        int floorLog2nr = Long.SIZE - Long.numberOfLeadingZeros(numberRevocations) - 1;
        int largestPowerOf2 = (1 << floorLog2nr);
        //Now we know where to place the "largestPowerOf2" values that will allow 
        //the worst-case revocations: on the right of the "1" bit from baseId.
        int shift = this.idLength - floorLog2nr;
        //==============================================================

        //Build the largestPowerOf2 IDs to be revoked
        for (long i = 0; i < largestPowerOf2; i++) {
            long id = baseId | (i << shift);
            list.add(id);
        }

        //Calculates the number of IDs that still need to be revoked
        numberRevocations -= largestPowerOf2;

        //Builds the next IDs to be revoked
        for (long i = 0; i < numberRevocations; i++) {
            long partialId = (i << 1) + 1;
            long id = baseId | (partialId << (shift - 1));
            list.add(id);
        }

        return list;
    }

    /**
     * Generates a list containing numberRevocations (pseudo)random long values
     * that correspond to leaf IDs (i.e., a bit '1' followed by
     * {@link #idLength} bits). The source of pseudorandomness is the given
     * seed.
     *
     * @param numberRevocations The number of leaf IDs to be generated.
     * @param seed The (pseudo)randomness seed
     * @return A list containing number (pseudo)random long values (not
     * necessarily ordered)
     */
    public ArrayList<Long> genRandom(int numberRevocations, long seed) {
        //Check if the number of revocations requested is valid
        if (!isSupported(numberRevocations)) {
            throw new IllegalArgumentException("Cannot generate " + numberRevocations
                    + " random values for an ID length of " + this.idLength);
        }

        //Places the leftmost 1 (to indicate a leaf) at the correct position
        long baseId = (1l << this.idLength);

        //We need a set containing distint (pseudo)random values
        HashSet<Long> set = new HashSet<>();

        //Instantiates and initializes the pseudorandom number generator (PRNG)
        Random prng = new Random();
        prng.setSeed(seed);

        //We will use two ints: this is the number of bits taken from each int
        int numBits = this.idLength / 2;
        //The mask for bit selection (ex.: 20 least significant bits for a 40-bit id)
        int mask = 0xFFFFFFFF >>> (Integer.SIZE - numBits);

        //Generates the required number of random Longs
        while (set.size() < numberRevocations) {
            long vid = baseId
                    | (((long) prng.nextInt() & mask) << numBits)
                    | (((long) prng.nextInt() & mask));
            //Let the Set data structure handle eventual repetitions
            //(collisions should not be too often for numberRevocations << 2^idLength
            set.add(vid);
        }

        //Turn the set into an Arraylist
        return new ArrayList(set);
    }

    /**
     * Counts the number of leaf nodes that can be derived by picking
     * {@link #idLength} nodes (i.e., the crowd size), following ACPC's FSS
     * unicast solution.
     *
     * @param tree The tree from which nodes will be picked. This implementation
     * only works for trees containing at least one revoked node. Note:
     * otherwise, the result is obvious... by picking the root, 2^tree_height
     * nodes can be derived.
     *
     * @return The counting result
     */
    public long countPrivacyFSS(ActivationTree tree) {
        //Number of codes to be requested by vehicles following the FSS approach
        int nodes2ask = this.idLength;

        //Number of vehicles that can be confused with the requester
        long crowdSize = 0;

        //Picks one node from each depth (as long as it has available nodes)
        for (int depth = 1; depth < this.treeHeight; depth++) {
            if (tree.countAvailableNodes(depth) > 0) {
                //Picks node from the depth
                tree.markPickedNode(depth);

                //This increases the crowd size accordingly
                crowdSize += tree.countDescendantLeaves(depth);

                //There is one less node to be picked
                nodes2ask--;
            }
        }

        //There may still be codes we need to request: pick more from top to 
        //bottom of the tree, until the total number of required nodes is 
        //picked or there are no pickable nodes left in the tree
        int depth = 1;
        while (nodes2ask > 0 && depth < this.treeHeight) {
            //We need more nodes and we did not ask for all nodes available
            if (tree.countAvailableNodes(depth) > 0) {
                //Picks node from the depth
                tree.markPickedNode(depth);

                //This increases the crowd size accordingly
                crowdSize += tree.countDescendantLeaves(depth);

                //There is one less node to be picked
                nodes2ask--;
            } else {
                //There is no node left at this depth: move to the next depth
                depth++;
            }
        }

        return crowdSize;
    }

    /**
     * Counts the number of leaf nodes that need to be picked to achieve the
     * desired crowd size, following ACPC's VSS unicast solution.
     *
     * @param tree The tree from which nodes will be picked. This implementation
     * only works for trees containing at least one revoked node. Note:
     * otherwise, the result is obvious... just pick one node, namely the root,
     * for maximum crowd size.
     * @param targetCrowd The desired crowd size
     *
     * @return The counting result
     */
    public long countAskedNodesVSS(ActivationTree tree, long targetCrowd) {
        //Number of nodes to be requested by vehicles following the FSS approach
        int askedNodes = 0;

        //Number of vehicles that can be confused with the requester
        long crowdSize = 0;

        //Picks one node from each depth (as long as it has available nodes)
        for (int depth = 1; depth < this.treeHeight; depth++) {
            if (tree.countAvailableNodes(depth) > 0) {
                //Picks node from the depth
                tree.markPickedNode(depth);

                //This increases the crowd size accordingly
                crowdSize += tree.countDescendantLeaves(depth);

                //One extra picked node
                askedNodes++;
            }
        }

        //We still need to ask some extra nodes to reach the target level of 
        //privacy
        int depth = 1;
        while (crowdSize < targetCrowd && depth < this.treeHeight) {
            //We need more nodes and we did not ask for all nodes available
            //at the current depth (shallowest first)
            if (tree.countAvailableNodes(depth) > 0) {
                //Picks node from the depth
                tree.markPickedNode(depth);

                //This increases the crowd size accordingly
                crowdSize += tree.countDescendantLeaves(depth);

                //One extra picked node
                askedNodes++;
            } else {
                //There is no node left at this depth: move to the next depth
                depth++;
            }
        }
        return askedNodes;
    }

    /**
     * Turns an ArrayList containing values of "long" type into a printable
     * array
     *
     * @param list The ArrayList to be printed
     * @return The printable string
     */
    public static String getBinaryString(ArrayList<Long> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        for (Long val : list) {
            sb.append(Long.toBinaryString(val));
            sb.append(" ");
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * Gives best, worst and average number of leaf nodes that can be derived by
     * picking log({@link #idLength}) nodes following ACPC's FSS unicast
     * solution.
     *
     * @param nRevocations The total number of revocations to be simulated
     * @param nAvg The number of tests to be considered when computing the
     * average
     * @param fw The filewriter pointing to the file where the results should be
     * written
     * @throws java.io.IOException If something goes wrong while writing to fw
     */
    public void sim4Revocations(int nRevocations, int nAvg, FileWriter fw)
            throws IOException {
        fw.write(Integer.toString(nRevocations));
        fw.write("\t");

        {//======================== BEST ======================//
            //Creates activation tree and revokes the required number of nodes,
            //assuming best case scenario
            ArrayList<Long> listBest = this.genBestCase(nRevocations);
            ActivationTree tree = new ActivationTree(this.idLength);
            tree.revoke(listBest);

            //Counts the obtained crowd size and prints result
            long privCount = this.countPrivacyFSS(tree);
            fw.write(Long.toString(privCount));
            fw.write("\t");
        }

        {//======================== WORST ======================//
            //Creates activation tree and revokes the required number of nodes,
            //assuming worst case scenario
            ArrayList<Long> listWorst = this.genWorstCase(nRevocations);
            ActivationTree tree = new ActivationTree(this.idLength);
            tree.revoke(listWorst);

            //Counts the obtained crowd size and prints result
            long privCount = this.countPrivacyFSS(tree);
            fw.write(Long.toString(privCount));
            fw.write("\t");
        }

        {//======================== AVERAGE ======================//
            //The average privacy, considering nAvg revocation scenarios
            double avgPriv = 0;
            for (int i = 0; i < nAvg; i++) {
                //Generates pseudorandom list of revocations, using some 
                //well-known constants to define the seed values
                ArrayList<Long> listRand = this.genRandom(nRevocations,
                        PLANK_CONSTANT + i * PI_CONSTANT);

                //Creates activation tree and revokes the required number of 
                //nodes, for a random distribution of revoked IDs
                ActivationTree tree = new ActivationTree(this.idLength);
                tree.revoke(listRand);

                //Counts the obtained crowd size
                long privCount = this.countPrivacyFSS(tree);

                //Adds the crowd size to an accumulator
                avgPriv += privCount;
            }
            //Computes the average crowd size
            avgPriv /= nAvg;

            //Counts the obtained crowd size and prints result
            fw.write(Double.toString(avgPriv));
            fw.write("\n");
        }
    }

    /**
     * Gives best, worst and average number of nodes that need to be picked to
     * achieve the desired crowd size, following ACPC's VSS unicast solution.
     *
     * @param nRevocations The total number of revocations to be simulated
     * @param nAvg The number of tests to be considered when computing the
     * average
     * @param targetCrowd The desired crowd size
     * @param fw The filewriter pointing to the file where the results should be
     * written
     * @throws java.io.IOException If something goes wrong while writing to fw
     */
    public void sim4TargetPrivacy(int nRevocations, int nAvg, long targetCrowd, FileWriter fw)
            throws IOException {
        fw.write(Integer.toString(nRevocations));
        fw.write("\t");

        //System.out.println("#Rev:\t" + nRevocations);
        {//======================== BEST ======================//
            //Creates activation tree and revokes the required number of nodes,
            //assuming best case scenario
            ArrayList<Long> listBest = this.genBestCase(nRevocations);
            ActivationTree tree = new ActivationTree(this.idLength);
            tree.revoke(listBest);

            //Counts the number of picked nodes and prints result
            long nodeCount = this.countAskedNodesVSS(tree, targetCrowd);
            fw.write(Long.toString(nodeCount));
            fw.write("\t");
        }

        {//======================== WORST ======================//
            //Creates activation tree and revokes the required number of nodes,
            //assuming worst case scenario
            ArrayList<Long> listWorst = this.genWorstCase(nRevocations);
            ActivationTree tree = new ActivationTree(this.idLength);
            tree.revoke(listWorst);

            //Counts the number of picked nodes and prints result
            long nodeCount = this.countAskedNodesVSS(tree, targetCrowd);
            fw.write(Long.toString(nodeCount));
            fw.write("\t");
        }

        {//======================== AVERAGE ======================//
            //The average node count, considering nAvg revocation scenarios
            double nodeCount = 0;
            //The maximum number of nodes that could be picked, on average
            double maxCount = 0;
            for (int i = 0; i < nAvg; i++) {
                //Generates pseudorandom list of revocations, using some 
                //well-known constants to define the seed values
                ArrayList<Long> listRand = this.genRandom(nRevocations, 
                        PLANK_CONSTANT + i * PI_CONSTANT);

                //Creates activation tree and revokes the required number of 
                //nodes, for a random distribution of revoked IDs
                ActivationTree tree = new ActivationTree(this.idLength);
                tree.revoke(listRand);

                //Counts the number of picked nodes, adding it to an accumulator
                long privCount = this.countAskedNodesVSS(tree, targetCrowd);
                nodeCount += privCount;
                
                //Counts the maximum number of nodes that could be picked, 
                //adding it to an accumulator
                maxCount += tree.countAllPickableNodes();
            }
            //Computes the average number of picked nodes
            nodeCount /= nAvg;
            //Computes the maximum number of pickable nodes, on average
            maxCount /= nAvg;
            
            //Prints the results
            fw.write(Double.toString(nodeCount));
            fw.write("\t");
            fw.write(Double.toString(maxCount));
            fw.write("\n");
        }
    }

    /**
     * Runs a simple simulation for VSS. Settings are hard-coded for simplicity
     */
    public static void simulateVSS() {

        //============== Configuration for simulation ============== 
        //Length of IDs in the activation tree
        int idLen = Simulator.DEFAULT_VID_LENGTH;
        //Average case will be computed over this many samples
        int SIM_COUNT = 10000;
        //Maximum number of revocations during simulation (from 1 to this many)
        int MAX_REVOCATIONS = 50000;
        //The number of revocations simulated will follow this step (set to 
        //1 for finer granularity)
        int REVOCATION_STEP = 100;
        //The crowd size should be this percetange of the total number of leaves
        int PERCENT_PRIVACY = 10;
        //The results will be written to this filename
        String FILENAME = "ACPC_SimVSS_"
                + MAX_REVOCATIONS + "+" + REVOCATION_STEP
                + " (" + SIM_COUNT + " repetitions," + " id " + idLen + ", " + PERCENT_PRIVACY + "% privacy).txt";
        //========================================================== 

        
        System.out.println("Filename is " + FILENAME);
        FileWriter fw = null;
        try {
            fw = new FileWriter(FILENAME, true);
            fw.write("#Rev\tBest\tWorst\tAvg\tAll\n");

            Simulator sim = new Simulator(idLen);

            long targetPrivacy = (2l << idLen) / 10;

            System.out.println("Simulating for target privacy of " + targetPrivacy + " nodes...");
            for (int nRevocations = REVOCATION_STEP; nRevocations <= MAX_REVOCATIONS; nRevocations += REVOCATION_STEP) {
                System.out.println(nRevocations);
                sim.sim4TargetPrivacy(nRevocations, SIM_COUNT, targetPrivacy, fw);
                fw.flush();
            }

            System.out.println("Results written to " + FILENAME);
        } catch (IOException ex) {
            System.out.println("I/O Error: " + ex.toString());
        } finally {
            try {
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                //nothing to do
            }
        }
    }

    /**
     * Runs a simple simulation for FSS. Settings are hard-coded for simplicity
     */
    public static void simulateFSS() {
        

        //============== Configuration for simulation ============== 
        //Length of IDs in the activation tree
        int idLen = Simulator.DEFAULT_VID_LENGTH;
        //Average case will be computed over this many samples
        int SIM_COUNT = 10000;
        //Maximum number of revocations during simulation (from 1 to this many)
        int MAX_REVOCATIONS = 50000;
        //The number of revocations simulated will follow this step (set to 
        //1 for finer granularity)
        int REVOCATION_STEP = 100;
        //The results will be written to this filename
        String FILENAME = "ACPC_SimFSS_"
                + MAX_REVOCATIONS + "+" + REVOCATION_STEP
                + " (" + SIM_COUNT + " repetitions," + " id " + idLen + ").txt";
        //========================================================== 

        System.out.println("Filename is " + FILENAME);
        FileWriter fw = null;
        try {
            fw = new FileWriter(FILENAME, true);
            fw.write("#Rev\tBest\tWorst\tAvg\n");

            Simulator sim = new Simulator(idLen);

            System.out.println("Simulating...");
            for (int nRevocations = REVOCATION_STEP; nRevocations <= MAX_REVOCATIONS; nRevocations += REVOCATION_STEP) {
                System.out.println(nRevocations);
                sim.sim4Revocations(nRevocations, SIM_COUNT, fw);
                fw.flush();
            }

            System.out.println("Results written to " + FILENAME);
        } catch (IOException ex) {
            System.out.println("I/O Error: " + ex.toString());
        } finally {
            try {
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                //nothing to do
            }
        }
    }

    public static void main(String[] args) {
        simulateFSS();
        simulateVSS();

    }

}
