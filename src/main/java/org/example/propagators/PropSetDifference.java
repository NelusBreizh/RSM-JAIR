package org.example.propagators;

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

public class PropSetDifference extends Propagator<SetVar> {
    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    private final SetVar Z;
    private final SetVar A;
    private final SetVar B;
    private final ISetDeltaMonitor[] sdm;
    private final Set<Integer> universeElements;
    private final IntProcedure ZForced;
    private final IntProcedure ZRemoved;
    private final IntProcedure AForced;
    private final IntProcedure ARemoved;
    private final IntProcedure BForced;
    private final IntProcedure BRemoved;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    /**
     * Ensures Z = A \ B
     *
     * @param setZ   set variable
     * @param setA   set variable
     * @param setB   set variable
     */
    public PropSetDifference(SetVar setZ, SetVar setA, SetVar setB) {
        super(new SetVar[]{setZ, setA, setB}, PropagatorPriority.UNARY, true);
        this.Z = setZ;
        this.A = setA;
        this.B = setB;
        this.universeElements = new HashSet<>();
        this.sdm = new ISetDeltaMonitor[]{this.Z.monitorDelta(this), this.A.monitorDelta(this), this.B.monitorDelta(this)};

        // DYNAMIC PROCEDURES
        this.ZForced = element -> {
            A.force(element, this);
            B.remove(element, this);
        };
        this.ZRemoved = element -> {
            if (A.getLB().contains(element)) {
                B.force(element, this);
            } else if (!(B.getUB().contains(element))) {
                A.remove(element,this);
            }
        };
        this.AForced = element -> {
            if (!(B.getUB().contains(element))) {
                Z.force(element, this);
            } else if (!(Z.getUB().contains(element))) {
                B.force(element, this);
            }
        };
        this.ARemoved = element -> {
            Z.remove(element,this);
        };
        this.BForced = element -> {
            Z.remove(element, this);
        };
        this.BRemoved = element -> {
            if (A.getLB().contains(element)) {
                Z.force(element, this);
            } else if (!(Z.getUB().contains(element))) {
                A.remove(element,this);
            }
        };
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        if (PropagatorEventType.isFullPropagation(evtmask)) {
            ISetIterator iter;
            for (SetVar X : new SetVar[]{Z, A, B}) {
               iter = X.getUB().iterator();
                while (iter.hasNext()) {
                    universeElements.add(iter.nextInt());
                }
            }
            for (int element : universeElements) {
                if (Z.getLB().contains(element)) {ZForced.execute(element);}
                if (!Z.getUB().contains(element)) {ZRemoved.execute(element);}
                if (A.getLB().contains(element)) {AForced.execute(element);}
                if (!A.getUB().contains(element)) {ARemoved.execute(element);}
                if (B.getLB().contains(element)) {BForced.execute(element);}
                if (!B.getUB().contains(element)) {BRemoved.execute(element);}
            }
            universeElements.clear();
            sdm[0].startMonitoring();
            sdm[1].startMonitoring();
            sdm[2].startMonitoring();
        }

    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        if (idxVarInProp ==  0) {
            sdm[idxVarInProp].forEach(ZForced, SetEventType.ADD_TO_KER);
            sdm[idxVarInProp].forEach(ZRemoved, SetEventType.REMOVE_FROM_ENVELOPE);
        } else if (idxVarInProp ==  1) {
            sdm[idxVarInProp].forEach(AForced, SetEventType.ADD_TO_KER);
            sdm[idxVarInProp].forEach(ARemoved, SetEventType.REMOVE_FROM_ENVELOPE);
        } else if (idxVarInProp ==  2) {
            sdm[idxVarInProp].forEach(BForced, SetEventType.ADD_TO_KER);
            sdm[idxVarInProp].forEach(BRemoved, SetEventType.REMOVE_FROM_ENVELOPE);
        }
    }

    @Override
    public ESat isEntailed() {
        if (isCompletelyInstantiated()) {
            ISetIterator iter;
            // Check that Z \subseteq A \ B
            iter = Z.getLB().iterator();
            while (iter.hasNext()) {
                int e = iter.nextInt();
                if (!(A.getUB().contains(e) && !B.getLB().contains(e))) {
                    return ESat.FALSE;
                }
            }
            // Check that A \ B \subseteq Z
            iter = A.getLB().iterator();
            while (iter.hasNext()) {
                int e = iter.nextInt();
                if (!B.getUB().contains(e) && !Z.getUB().contains(e)) {
                    return ESat.FALSE;
                }
            }
            // We have Z = A \ B
            return ESat.TRUE;
        }
        return ESat.UNDEFINED;
    }

}