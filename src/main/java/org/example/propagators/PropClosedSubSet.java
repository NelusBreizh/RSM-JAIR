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
import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.chocosolver.util.procedure.IntProcedure;

public class PropClosedSubSet extends Propagator<SetVar> {
    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    private final SetVar subSet;
    private final int offSet;
    private final DirectedGraph poSet;
    private final ISetDeltaMonitor sdm;
    private final IntProcedure subSetForced;
    private final IntProcedure subSetRemoved;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public PropClosedSubSet(SetVar subSet, DirectedGraph poSet) {
        this(subSet, 0, poSet);
    }

    /**
     * Ensures subSet is a closed subset according to poSet
     *
     * @param subSet   set variable
     * @param offset   int representing the offset of subSet compare to the nodes in poSet (0..n-1)
     * @param poSet    the directed acyclic graph (DAG) representing a partially ordered set.
     *                 For performance purposes, the DAG should be its own transitive reduction
     */
    public PropClosedSubSet(SetVar subSet, int offset, DirectedGraph poSet) {
        super(new SetVar[]{subSet}, PropagatorPriority.UNARY, true);
        this.subSet = subSet;
        this.offSet = offset;
        this.poSet = poSet;
        this.sdm = this.subSet.monitorDelta(this);

        // DYNAMIC PROCEDURES
        this.subSetForced = element -> {
            for (int pred : poSet.getPredecessorsOf(element - offSet)) {
                // The cause is null to allow self propagation
                subSet.force(pred + offset, Cause.Null);
            }
        };
        this.subSetRemoved = element -> {
            for (int succ : poSet.getSuccessorsOf(element - offSet)) {
                // The cause is null to allow self propagation
                subSet.remove(succ + offSet, Cause.Null);
            }
        };
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        if (PropagatorEventType.isFullPropagation(evtmask)) {
            cleanSubSet();
            // Start monitoring to allow self propagation after the initial propagator call
            sdm.startMonitoring();
            for (int x = 0; x < poSet.getNbMaxNodes(); x++) {
                if (subSet.getLB().contains(x + offSet)) {
                    subSetForced.execute(x + offSet);
                }
                if (!subSet.getUB().contains(x + offSet)) {
                    subSetRemoved.execute(x + offSet);
                }
            }
        }
    }

    public void cleanSubSet() throws ContradictionException {
        int val;
        ISetIterator valIter = subSet.getUB().iterator();
        while (valIter.hasNext()) {
            val = valIter.nextInt();
            if (val  - offSet < 0 || val - offSet >= poSet.getNbMaxNodes()) {
                subSet.remove(val, this);
            }
        }
    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        if (idxVarInProp == 0) {
            sdm.forEach(subSetForced, SetEventType.ADD_TO_KER);
            sdm.forEach(subSetRemoved, SetEventType.REMOVE_FROM_ENVELOPE);
        }
    }

    @Override
    public ESat isEntailed() {
        if (isCompletelyInstantiated()) {
            ISetIterator iter = subSet.getLB().iterator();
            while (iter.hasNext()) {
                int x = iter.nextInt();
                for (int pred : poSet.getPredecessorsOf(x)) {
                    if (!(subSet.getUB().contains(pred))) {
                        return ESat.FALSE;
                    }
                }
            }
            return ESat.TRUE;
        }
        return ESat.UNDEFINED;
    }
}