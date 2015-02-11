package org.thingml.comm.rxtx.adaptation;

import org.kevoree.*;
import org.kevoree.api.adaptation.AdaptationModel;
import org.kevoree.api.adaptation.AdaptationPrimitive;
import org.kevoree.api.adaptation.AdaptationType;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.KMFContainer;
import org.kevoree.pmodeling.api.compare.ModelCompare;
import org.kevoree.pmodeling.api.trace.*;

import java.util.HashMap;

/**
 * Created by leiko on 11/02/15.
 */
public class AdaptationEngine {

    private ModelCompare compare;

    public AdaptationEngine() {
        KevoreeFactory factory = new DefaultKevoreeFactory();
        this.compare = factory.createModelCompare();
    }

    /**
     * Creates a diff between current and target model and returns a resulting AdaptationModel
     * @param currentModel
     * @param targetModel
     * @return
     */
    public AdaptationModel diff(ContainerRoot currentModel, ContainerRoot targetModel) {
        TraceSequence s = this.compare.diff(currentModel, targetModel);
        HashMap<String, TupleObjPrim> elementAlreadyProcessed = new HashMap<String, TupleObjPrim>();

        AdaptationModel adaptationModel = new AdaptationModel();
        ContainerNode targetNode = targetModel.findNodesByID("MySintefNode");

        for (ModelTrace trace : s.getTraces()) {
            KMFContainer modelElement = targetModel.findByPath(trace.getSrcPath());
            if (trace.getRefName().equals("components")) {
                if (trace.getSrcPath().equals(targetNode.path())) {
                    if (trace instanceof ModelAddTrace) {
                        KMFContainer elemToAdd = targetModel.findByPath(((ModelAddTrace) trace).getPreviousPath());
                        adaptationModel.getAdaptations().add(adapt(AdaptationType.AddInstance, elemToAdd));
                    }
                    if (trace instanceof ModelRemoveTrace) {
                        KMFContainer elemToAdd = currentModel.findByPath(((ModelRemoveTrace) trace).getObjPath());
                        adaptationModel.getAdaptations().add(adapt(AdaptationType.RemoveInstance, elemToAdd));
                        adaptationModel.getAdaptations().add(adapt(AdaptationType.StopInstance, elemToAdd));
                    }
                }
            }
            if (trace.getRefName().equals("bindings")) {
                if (!(targetModel.findByPath(trace.getSrcPath()) instanceof Channel)) {
                    if (trace instanceof ModelAddTrace) {
                        MBinding binding = (MBinding) targetModel.findByPath(((ModelAddTrace) trace).getPreviousPath());
                        adaptationModel.getAdaptations().add(adapt(AdaptationType.AddBinding, binding));
                        Channel channel = binding.getHub();
                        if (channel != null) {
                            TupleObjPrim newTuple = new TupleObjPrim(channel, AdaptationType.AddInstance);
                            if (!elementAlreadyProcessed.containsKey(newTuple.getKey())) {
                                adaptationModel.getAdaptations().add(adapt(AdaptationType.AddInstance, channel));
                                elementAlreadyProcessed.put(newTuple.getKey(), newTuple);
                            }
                        }
                    }
                    if (trace instanceof ModelRemoveTrace) {
                        MBinding binding = (MBinding) currentModel.findByPath(((ModelRemoveTrace) trace).getObjPath());
                        MBinding previousBinding = (MBinding) currentModel.findByPath(((ModelRemoveTrace) trace).getObjPath());
                        Channel channel = binding.getHub();
                        Channel oldChannel = previousBinding.getHub();
                        // check if not no current usage of this channel
                        boolean stillUsed = channel != null;
                        if (channel != null) {
                            for (MBinding loopBinding : channel.getBindings()) {
                                if (loopBinding.getPort() != null) {
                                    if (loopBinding.getPort().eContainer().equals(targetNode)) {
                                        stillUsed = true;
                                    }
                                }
                            }
                        }
                        if (!stillUsed) {
                            TupleObjPrim removeTuple = new TupleObjPrim(oldChannel, AdaptationType.RemoveInstance);
                            if (!elementAlreadyProcessed.containsKey(removeTuple.getKey())) {
                                adaptationModel.getAdaptations().add(adapt(AdaptationType.RemoveInstance, oldChannel));
                                elementAlreadyProcessed.put(removeTuple.getKey(), removeTuple);
                                TupleObjPrim stopTuple = new TupleObjPrim(oldChannel, AdaptationType.StopInstance);
                                elementAlreadyProcessed.put(stopTuple.getKey(), stopTuple);
                            }
                        }
                        adaptationModel.getAdaptations().add(
                                adapt(AdaptationType.RemoveBinding, binding));
                    }
                }
            }
            if (trace.getRefName().equals("started")) {
                if (modelElement instanceof Instance
                        && trace instanceof ModelSetTrace) {
                    if (modelElement.eContainer() instanceof ContainerNode
                            && !modelElement.eContainer().path()
                            .equals(targetNode.path())) {
                        // ignore it, for another node
                    } else {
                        if (trace.getSrcPath().equals(targetNode.path())) {
                            // HaraKiri case
                        } else {
                            if (((ModelSetTrace) trace).getContent().toLowerCase().equals("true")) {
                                TupleObjPrim sIT = new TupleObjPrim(modelElement, AdaptationType.StartInstance);
                                if (!elementAlreadyProcessed.containsKey(sIT.getKey())) {
                                    adaptationModel.getAdaptations().add(adapt(AdaptationType.StartInstance, modelElement));
                                    elementAlreadyProcessed.put(sIT.getKey(), sIT);
                                }
                            } else {
                                TupleObjPrim sit = new TupleObjPrim(modelElement, AdaptationType.StopInstance);
                                if (!elementAlreadyProcessed.containsKey(sit.getKey())) {
                                    adaptationModel.getAdaptations().add(adapt(AdaptationType.StopInstance, modelElement));
                                    elementAlreadyProcessed.put(sit.getKey(), sit);
                                }
                            }
                        }
                    }
                }
            }

        }

        return adaptationModel;
    }

    /* Helper to create command */
    private AdaptationPrimitive adapt(AdaptationType primitive, Object elem) {
        AdaptationPrimitive ccmd = new AdaptationPrimitive();
        ccmd.setPrimitiveType(primitive.name());
        ccmd.setRef(elem);
        return ccmd;
    }

    private class TupleObjPrim {
        private KMFContainer obj;

        private TupleObjPrim(KMFContainer obj, AdaptationType p) {
            this.obj = obj;
            this.p = p;
        }

        public AdaptationType getP() {
            return p;
        }

        public KMFContainer getObj() {
            return obj;
        }

        private AdaptationType p;

        @Override
        public boolean equals(Object second) {
            if (!(second instanceof TupleObjPrim)) {
                return false;
            } else {
                TupleObjPrim secondTuple = (TupleObjPrim) second;
                return secondTuple.getObj().equals(getObj())
                        && secondTuple.getP().equals(p);
            }
        }

        public String getKey() {
            return getObj().path() + "/" + p.name();
        }

    }
}
