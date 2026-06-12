package org.example.propagators;

import org.chocosolver.solver.Cause;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.delta.ISetDeltaMonitor;
import org.chocosolver.solver.variables.events.PropagatorEventType;
import org.chocosolver.solver.variables.events.SetEventType;
import org.chocosolver.util.ESat;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.chocosolver.util.procedure.IntProcedure;

import java.util.HashSet;
import java.util.Set;

public class PropIndexDisjointUnion extends Propagator<SetVar> {
    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    private final SetVar indices;
    private final int offSetInd;
    private final SetVar elements;
    private final int offSetEle;
    private final int minEle;
    private final int maxEle;
    private final Set<Integer> universeElements;
    private final int[][] fixedSets;
    private final int[] uniqueIndex;
    private final ISetDeltaMonitor[] sdm;
    private final IntProcedure indicesForced;
    private final IntProcedure indicesRemoved;
    private final IntProcedure elementsForced;
    private final IntProcedure elementsRemoved;

    /*
     * Remark on some notations: because of potential offsets, we must distinguish the INDICES and ELEMENTS whether we consider them as values in the set variables, indices in arrays, etc
     * - i stands for the array index of an INDEX in the array "fixedSets"
     * - iVal stands for the value of an INDEX in the set variable "indices"
     * - e stands for the array index of an ELEMENT in the array "uniqueIndex"
     * - eVal stands for the value of an ELEMENT in the set variable "elements"
     * - ele stands for an ELEMENT as it is stored in the sets for "fixedSets"
     * Therefore we have:
     * - i = iVal - offSetInd
     * - ele = eVal - offSetEle
     * - e  = ele - minEle
     */

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public PropIndexDisjointUnion(SetVar setInd, SetVar setEle, int[][] fixedSets, boolean b) {
        this(setInd, 0, setEle, 0, fixedSets);
    }

    public PropIndexDisjointUnion(SetVar setInd, SetVar setEle, int[][] fixedSets) {
        this(setInd, 0, setEle, 0, fixedSets);
    }

    /**
     * Given a set variable "setInd" representing INDICES, a set variable "setEle" representing ELEMENTS and a family of pairwise disjoint fixed sets,
     * ensures that setEle is the union of all fixed sets whose index is in setInd
     *
     * @param setInd set variable representing the INDICES
     * @param setEle set variable representing the ELEMENTS
     * @param offsetInd int representing the offset of setInd compare to 0
     * @param offsetEle int representing the offset of setEle compare to the ELEMENTS stored in the sets of fixedsets
     * @param fixedsets int[][] representing the family of pairwise disjoint fixed sets
     */
    public PropIndexDisjointUnion(SetVar setInd, int offsetInd, SetVar setEle, int offsetEle, int[][] fixedsets) {
        super(new SetVar[]{setInd, setEle}, PropagatorPriority.UNARY, true);
        this.indices = setInd;
        this.elements = setEle;
        this.offSetInd = offsetInd;
        this.offSetEle = offsetEle;
        this.fixedSets = fixedsets;
        this.universeElements = new HashSet<>();
        int tempMinEle = fixedsets[0][0];
        int tempMaxEle = fixedsets[0][0];
        for (int[] set : fixedsets) {
            for (int ele : set) {
                tempMinEle = Math.min(tempMinEle, ele);
                tempMaxEle = Math.max(tempMaxEle, ele);
                universeElements.add(ele + offSetEle);
            }
        }
        this.minEle = tempMinEle;
        this.maxEle = tempMaxEle;
        this.uniqueIndex = new int[maxEle - minEle + 1];
        for (int i = 0; i < fixedsets.length; i++) {
            for (int ele : fixedsets[i]) {
                uniqueIndex[ele - minEle] = i;
            }
        }
        this.sdm = new ISetDeltaMonitor[]{this.indices.monitorDelta(this), this.elements.monitorDelta(this)};

        // DYNAMIC PROCEDURES
        // Propagating over an INDEX sets the state of all values in {INDEX} U SET_INDEX
        this.indicesForced = iVal -> {
            for (int ele : fixedSets[iVal - offSetInd]) {
                elements.force(ele + offSetEle, this);
            }
        };
        this.indicesRemoved = iVal -> {
            for (int ele : fixedSets[iVal - offSetInd]) {
                elements.remove(ele + offSetEle, this);
            }
        };
        // The cause is null to allow self propagation, this way propagation is performed later over INDEX to set the state of all values in {INDEX} U SET_INDEX
        this.elementsForced = eVal -> {
            indices.force(uniqueIndex[eVal - offSetEle - minEle] + offSetInd, Cause.Null);
        };
        this.elementsRemoved = eVal -> {
            indices.remove(uniqueIndex[eVal - offSetEle - minEle] + offSetInd, Cause.Null);
        };
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        if (PropagatorEventType.isFullPropagation(evtmask)) {
            cleanIndices();
            cleanElements();
            // Start monitoring to allow self propagation after the initial propagator call
            sdm[0].startMonitoring();
            sdm[1].startMonitoring();
            for (int i = 0; i < fixedSets.length; i++) {
                if (indices.getLB().contains(i + offSetInd)) {
                    indicesForced.execute(i + offSetInd);
                }
                if (!indices.getUB().contains(i + offSetInd)) {
                    indicesRemoved.execute(i + offSetInd);
                }
            }
            for (int ele : universeElements) {
                if (elements.getLB().contains(ele + offSetEle)) {
                    elementsForced.execute(ele + offSetEle);
                }
                if (!elements.getUB().contains(ele + offSetEle)) {
                    elementsRemoved.execute(ele + offSetEle);
                }
            }
            // No need of universeElements any more
            universeElements.clear();
        }
    }

    public void cleanIndices() throws ContradictionException {
        int iVal;
        ISetIterator iValIter = indices.getUB().iterator();
        while (iValIter.hasNext()) {
            iVal = iValIter.nextInt();
            if (iVal  - offSetInd < 0 || iVal - offSetInd >= fixedSets.length) {
                indices.remove(iVal, this);
            }
        }
    }

    public void cleanElements() throws ContradictionException {
        int eVal;
        ISetIterator eValIter = elements.getUB().iterator();
        while (eValIter.hasNext()) {
            eVal = eValIter.nextInt();
            if (!universeElements.contains(eVal)) {
                elements.remove(eVal, this);
            }
        }
    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        if (idxVarInProp ==  0) {
            sdm[idxVarInProp].forEach(indicesForced, SetEventType.ADD_TO_KER);
            sdm[idxVarInProp].forEach(indicesRemoved, SetEventType.REMOVE_FROM_ENVELOPE);
        } else if (idxVarInProp ==  1) {
            sdm[idxVarInProp].forEach(elementsForced, SetEventType.ADD_TO_KER);
            sdm[idxVarInProp].forEach(elementsRemoved, SetEventType.REMOVE_FROM_ENVELOPE);
        }
    }

    @Override
    public ESat isEntailed() {
        if (!isCompletelyInstantiated()) {
            return ESat.UNDEFINED;
        }
        ISetIterator iValIter = indices.getLB().iterator();
        while (iValIter.hasNext()){
            for (int ele : fixedSets[iValIter.nextInt() - offSetInd]){
                if (!(elements.getUB().contains(ele + offSetEle))) {
                    return ESat.FALSE;
                }
            }
        }
        ISetIterator eValIter = elements.getLB().iterator();
        while (eValIter.hasNext()){
            if (!(indices.getUB().contains(uniqueIndex[eValIter.nextInt() - offSetEle - minEle] + offSetInd))) {
                return ESat.FALSE;
            }
        }
        return ESat.TRUE;
    }

}