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

/**
 * Created by leiko on 11/02/15.
 */
public class Adaptations2Commands {

    public Set<SerialCommand> process(AdaptationModel model) {
        Set<SerialCommand> cmds = new TreeSet<SerialCommand>(new Comparator<SerialCommand>() {
            public int compare(SerialCommand o1, SerialCommand o2) {
                return o2.priority() - o1.priority();
            }
        });

        if (model != null) {
            for (AdaptationPrimitive p : model.getAdaptations()) {
                if (p.getPrimitiveType().equals(AdaptationType.AddInstance.name())) {
                    cmds.add(new TaskInstantiate(
                            ((ComponentInstance) p.getRef()).getTypeDefinition().getName(),
                            ((ComponentInstance) p.getRef()).getName()
                    ));

                } else if (p.getPrimitiveType().equals(AdaptationType.RemoveInstance.name())) {
                    // TODO

                } else if (p.getPrimitiveType().equals(AdaptationType.AddBinding.name())) {
                    Channel hub = ((MBinding) p.getRef()).getHub();
                    Port port0 = hub.getBindings().get(0).getPort();
                    Port port1 = hub.getBindings().get(1).getPort();

                    if (((ComponentInstance) port0.eContainer()).getTypeDefinition()
                            .getDeployUnits().get(0).getName().startsWith("sintef")
                            && ((ComponentInstance) port1.eContainer())
                            .getTypeDefinition().getDeployUnits().get(0)
                            .getName().startsWith("sintef")
                            && hub.getBindings().size() == 2) {

                        cmds.add(new ChannelCreate(
                                ((ComponentInstance) port0.eContainer()).getName(),
                                port0.getPortTypeRef().getName().replace("tx", "").replace("rcv", ""),
                                ((ComponentInstance) port1.eContainer()).getName(),
                                port1.getPortTypeRef().getName().replace("tx", "").replace("rcv", "")
                        ));
                    }
                } else if (p.getPrimitiveType().equals(AdaptationType.RemoveBinding.name())) {
                    Channel hub = ((MBinding) p.getRef()).getHub();
                    Port port0 = hub.getBindings().get(0).getPort();
                    Port port1 = hub.getBindings().get(1).getPort();

                    if (((ComponentInstance) port0.eContainer()).getTypeDefinition()
                            .getDeployUnits().get(0).getName().startsWith("sintef")
                            && ((ComponentInstance) port1.eContainer())
                            .getTypeDefinition().getDeployUnits().get(0)
                            .getName().startsWith("sintef")
                            && hub.getBindings().size() == 2) {
                        cmds.add(new ChannelDelete(
                                ((ComponentInstance) port0.eContainer()).getName(),
                                port0.getPortTypeRef().getName().replace("tx", "").replace("rcv", ""),
                                ((ComponentInstance) port1.eContainer()).getName(),
                                port1.getPortTypeRef().getName().replace("tx", "").replace("rcv", "")
                        ));
                    }
                } else if (p.getPrimitiveType().equals(AdaptationType.StartInstance.name())) {
                    cmds.add(new TaskResume(((ComponentInstance) p.getRef()).getName()));

                } else if (p.getPrimitiveType().equals(AdaptationType.StopInstance.name())) {
                    cmds.add(new TaskSuspend(((ComponentInstance) p.getRef()).getName()));
                }
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

        return cmds;
    }
}
