package org.thingml.comm.rxtx.adaptation;

import org.kevoree.Channel;
import org.kevoree.ComponentInstance;
import org.kevoree.MBinding;
import org.kevoree.Port;
import org.kevoree.api.adaptation.AdaptationModel;
import org.kevoree.api.adaptation.AdaptationPrimitive;
import org.kevoree.api.adaptation.AdaptationType;
import org.kevoree.log.Log;
import org.thingml.comm.rxtx.serial.protocol.SerialCommand;
import org.thingml.comm.rxtx.serial.protocol.channels.ChannelCreate;
import org.thingml.comm.rxtx.serial.protocol.channels.ChannelDelete;
import org.thingml.comm.rxtx.serial.protocol.tasks.TaskCreate;
import org.thingml.comm.rxtx.serial.protocol.tasks.TaskInstantiate;
import org.thingml.comm.rxtx.serial.protocol.tasks.TaskResume;
import org.thingml.comm.rxtx.serial.protocol.tasks.TaskSuspend;

import java.util.*;
import org.kevoree.pmodeling.api.KMFContainer;

/**
 * Created by leiko on 11/02/15.
 */
public class Adaptations2Commands {

    public List<SerialCommand> process(AdaptationModel model) {
        int loopCount = -1;
        List<SerialCommand> cmds = new ArrayList<SerialCommand>();

        if (model != null) {
            for (AdaptationPrimitive p : model.getAdaptations()) {
                loopCount++;
                System.err.println("loopCount " + loopCount + " primitive : " + p.getPrimitiveType().toString());
                System.err.println("loopCount " + loopCount + " model entity : " + ((KMFContainer)p.getRef()).path());
                
                if (p.getPrimitiveType().equals(AdaptationType.AddInstance.name())) {
                    if ( p.getRef() instanceof ComponentInstance) {
                        System.err.println("If AddInstance..." + loopCount);
                        cmds.add(new TaskInstantiate(
                                ((ComponentInstance) p.getRef()).getTypeDefinition().getName(),
                                ((ComponentInstance) p.getRef()).getName()
                        ));
                    }

                } else if (p.getPrimitiveType().equals(AdaptationType.RemoveInstance.name())) {
                    if ( p.getRef() instanceof ComponentInstance) {
                        System.err.println("If RemoveInstance..." + loopCount);
                    }
                    // TODO

                } else if (p.getPrimitiveType().equals(AdaptationType.AddBinding.name())) {
                    System.err.println("If AddBinding..." + loopCount);

                    Channel hub = ((MBinding) p.getRef()).getHub();
                    Port port0 = hub.getBindings().get(0).getPort();
                    Port port1 = hub.getBindings().get(1).getPort();

                    if (((ComponentInstance) port0.eContainer()).getTypeDefinition()
                            .getDeployUnits().get(0).getName().startsWith("sintef")
                            && ((ComponentInstance) port1.eContainer())
                            .getTypeDefinition().getDeployUnits().get(0)
                            .getName().startsWith("sintef")
                            && hub.getBindings().size() == 2) {

                        System.err.println("If AddBinding sintef..." + loopCount);
                        cmds.add(new ChannelCreate(
                                ((ComponentInstance) port0.eContainer()).getName(),
                                port0.getPortTypeRef().getName().replace("tx", "").replace("rcv", ""),
                                ((ComponentInstance) port1.eContainer()).getName(),
                                port1.getPortTypeRef().getName().replace("tx", "").replace("rcv", "")
                        ));
                    }
                } else if (p.getPrimitiveType().equals(AdaptationType.RemoveBinding.name())) {
                    System.err.println("If RemoveBinding..." + loopCount);

                    Channel hub = ((MBinding) p.getRef()).getHub();
                    Port port0 = hub.getBindings().get(0).getPort();
                    Port port1 = hub.getBindings().get(1).getPort();

                    if (((ComponentInstance) port0.eContainer()).getTypeDefinition()
                            .getDeployUnits().get(0).getName().startsWith("sintef")
                            && ((ComponentInstance) port1.eContainer())
                            .getTypeDefinition().getDeployUnits().get(0)
                            .getName().startsWith("sintef")
                            && hub.getBindings().size() == 2) {
                        System.err.println("If RemoveBinding sintef..." + loopCount);
                        cmds.add(new ChannelDelete(
                                ((ComponentInstance) port0.eContainer()).getName(),
                                port0.getPortTypeRef().getName().replace("tx", "").replace("rcv", ""),
                                ((ComponentInstance) port1.eContainer()).getName(),
                                port1.getPortTypeRef().getName().replace("tx", "").replace("rcv", "")
                        ));
                    }
                } else if (p.getPrimitiveType().equals(AdaptationType.StartInstance.name())) {
                    if ( p.getRef() instanceof ComponentInstance) {
                        System.err.println("If StartInstance..." + loopCount);
                        cmds.add(new TaskResume(((ComponentInstance) p.getRef()).getName()));
                    }

                } else if (p.getPrimitiveType().equals(AdaptationType.StopInstance.name())) {
                    if ( p.getRef() instanceof ComponentInstance) {
                        System.err.println("If StopInstance..." + loopCount);
                        cmds.add(new TaskSuspend(((ComponentInstance) p.getRef()).getName()));
                    }
                }
                System.err.println("Loop next..." + loopCount);
            }

            // TODO Note from Max:
            // TODO This is why I said earlier: "how to handle instance creation?"
            // TODO How can you know which instances to start or no, if in the end you only have one command
            // TODO that forces each component to be instantiated
            Log.info("{} modelUpdated() 2");
            if (model.getAdaptations().size() > 0) {
                cmds.add(new TaskCreate());
            }
        }

        Collections.sort(cmds, new Comparator<SerialCommand>() {
            public int compare(SerialCommand o1, SerialCommand o2) {
                return o1.priority() - o2.priority();
            }
        });
        return cmds;
    }
}
