package org.thingml.comm.rxtx.serial;

import org.kevoree.*;
import org.kevoree.Package;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UpdateCallback;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.log.Log;
import org.thingml.comm.rxtx.ChannelChecker;

/**
 * Created by leiko on 11/02/15.
 */
public class SerialInterpreter implements SerialObserver {

    private KevoreeFactory factory = new DefaultKevoreeFactory();
    private ModelService modelService;
    private ContainerRoot rx_root;
    private ChannelChecker channelChecker;

    private enum ScanStates { IDLE, INTERRUPTED, TASK_INSTANCE_START, TASK_INSTANCE_END, TASK_TYPE_START, TASK_TYPE_END, CHANNEL_START, CHANNEL_END, COMPLETED, FAILED};
    private ScanStates scanState;

    private final String compTypeVersionDefault = "1.0.0";

    public SerialInterpreter(ModelService modelService, ChannelChecker channelChecker) {
        this.modelService = modelService;
        this.channelChecker = channelChecker;
        scanState = ScanStates.IDLE;
    }
    
    public String getRequestString() {
        String ret = "";
        
        if (scanState == ScanStates.INTERRUPTED) {
            scanState = ScanStates.IDLE;
            Log.info("getRequestString() skip scan after resetInterpreter()");
            return ret;
        }
        
        ret = ret + "info \r\n";
        ret = ret + "task types \r\n";
        ret = ret + "task active \r\n";
        ret = ret + "channel list \r\n";
        return ret;
    }
    
    synchronized public void resetInterpreter() {
        //Log.info("resetInterpreter()");
        scanState = ScanStates.INTERRUPTED;
    }

    synchronized public void receive(String data) {
        Log.info("{} PROMPT " + data);
        if (!data.startsWith("HEADS->norm>")) {
            if (data.equals("Listing of supported task types")) {
                if (scanState == ScanStates.INTERRUPTED) {
                    Log.info("receive() skip scan after resetInterpreter()");
                    scanState = ScanStates.IDLE;
                } else {
                    scanState = ScanStates.TASK_TYPE_START;
                    InitForNewScan();
                }
            }

            if (scanState == ScanStates.INTERRUPTED) 
                return;

            if (scanState == ScanStates.IDLE) 
                return;

            if (data.equals("End of task type listing")) {
                if (scanState == ScanStates.TASK_TYPE_START) {
                    scanState = ScanStates.TASK_TYPE_END;
                } else {
                    Log.info("receive() unexpected3 input sequence => ERROR");
                    scanState = ScanStates.FAILED;
                }
            }

            if (data.equals("Listing of current task instances")) {
                if (scanState == ScanStates.TASK_TYPE_END) {
                    scanState = ScanStates.TASK_INSTANCE_START;
                } else {
                    Log.info("receive() unexpected2 input sequence => ERROR");
                    scanState = ScanStates.FAILED;
                }
            }

            if (data.equals("End of task instance listing")) {
                if (scanState == ScanStates.TASK_INSTANCE_START) {
                    scanState = ScanStates.TASK_INSTANCE_END;
                } else {
                    Log.info("receive() unexpected1 input sequence => ERROR");
                    scanState = ScanStates.FAILED;
                }
            }
        
            if (data.equals("Listing of channels connected to current task instances")) {
                if (scanState == ScanStates.TASK_INSTANCE_END) {
                    scanState = ScanStates.CHANNEL_START;
                } else {
                    Log.info("receive() unexpected4 input sequence => ERROR");
                    scanState = ScanStates.FAILED;
                }
            }

            if (data.equals("End of channel listing")) {
                if (scanState == ScanStates.CHANNEL_START) {
                    scanState = ScanStates.CHANNEL_END;
                    if (rx_root != null) {
                        modelService.update(rx_root, new UpdateCallback() {
                            public void run(Boolean applied) {
                                scanState = ScanStates.COMPLETED;
                                Log.info("End of channel listing call to modelService.update() => applied=" + applied);
                            }
                        });
                    }
                } else {
                    Log.info("receive() unexpected5 input sequence => ERROR");
                    scanState = ScanStates.FAILED;
                }
            }
            
            switch(scanState) {
                case TASK_INSTANCE_START:
                    ProcessLineIfTaskInstance(data);
                    break;
                case TASK_TYPE_START:
                    ProcessLineIfTaskType(data);
                    break;
                case CHANNEL_START:
                    ProcessLineIfChannel(data);
                    break;
                default:
                    break;
            }
        }
    }

    synchronized private void InitForNewScan() {
        org.kevoree.Package rx_pkg;
        
        rx_pkg = factory.createPackage();
        rx_pkg.setName("sintef");
        channelChecker.prepareNewInterpretion();

        org.kevoree.DeployUnit du = factory.createDeployUnit();
        du.setName("sintefnodetype");
        du.setVersion("1.0.0");
        rx_pkg.addDeployUnits(du);

        TypeDefinition tf = factory.createNodeType();
        tf.setName("sintefnodetype");
        tf.setVersion("1.0.0");
        tf.addDeployUnits(du);

        rx_pkg.addTypeDefinitions(tf);

        org.kevoree.DeployUnit du1 = factory.createDeployUnit();
        du1.setName("sintefchannel");
        du1.setVersion("1.0.0");
        rx_pkg.addDeployUnits(du1);

        ChannelType tf1 = factory.createChannelType();
        tf1.setName("sintefchannel");
        tf1.setVersion("1.0.0");
        tf1.addDeployUnits(du1);

        //rx_pkg.addTypeDefinitions(tf);
        rx_pkg.addTypeDefinitions(tf1);

        ContainerNode n = factory.createContainerNode();
        n.setStarted(true);
        n.setName("MySintefNode");
        n.setTypeDefinition(tf);
        rx_root = factory.createModelCloner().clone(modelService.getCurrentModel().getModel());
        factory.root(rx_root);

        rx_root.addPackages(rx_pkg);
        rx_root.addNodes(n);
        n.setGroups(rx_root.getGroups());
        
        FintAndPrintTypedef("End of InitForNewScan()");
    }
    
    private void FintAndPrintTypedef(String pos){
        System.err.println(" ********************************************************** " );
        System.err.print(pos + " package deployUnits [ " );
        for( DeployUnit du :rx_root.findPackagesByID("sintef").getDeployUnits()) {
            System.err.print(du.getName() + " | " + du + " , ");
        }
        System.err.println( " ] ");
        
        System.err.println(pos + " package packages [ ");
        for( Package du :rx_root.findPackagesByID("sintef").getPackages()) {
            System.err.print(du.getName() + " | " + du + " , ");
        }
        System.err.println( " ] ");

        System.err.println(pos + " package typeDefinitions [ ");
        for( TypeDefinition du :rx_root.findPackagesByID("sintef").getTypeDefinitions()) {
            System.err.print(du.getName() + " | " + du + " , ");
        }
        System.err.println( " ] ");
        System.err.println(pos + " node componentInstances [ ");
        for( ComponentInstance du :rx_root.findNodesByID("MySintefNode").getComponents()) {
            System.err.print(du.getName() + " | " + du + " , ");
        }
        System.err.println( " ] ");
        System.err.println(" ********************************************************** " );
    }
    
    synchronized private void ProcessLineIfTaskInstance(String data) {
        if (data.startsWith("Task type=") && data.contains("instance=")) {
            String[] s1 = data.replace("Task type=", "").replace("instance=", "").replace("state=", "").split(" ");
            String tid = "name=" + s1[0] + ",version="+compTypeVersionDefault;
            String iid = s1[1];
            boolean started = s1[2].equals("RUN");
            TypeDefinition t = rx_root.findPackagesByID("sintef").findTypeDefinitionsByID(tid);
            ComponentInstance instance = rx_root.findNodesByID("MySintefNode").findComponentsByID(iid);
            //Log.info("{} Type def(" + tid + ")=" + t);
            //Log.info("{} Type inst=" + instance);

            if (t == null) {
                Log.info("Task type <" + tid + "> not found ... creating");
                t = factory.createTypeDefinition();
                t.setName(tid);

                rx_root.findPackagesByID("sintef").addTypeDefinitions(t);
            }
            if (instance == null) {
                Log.info("Task instance <" + iid + "> not found ... creating");
                instance = factory.createComponentInstance();
                instance.setTypeDefinition(t);
                instance.setName(iid);
                instance.setStarted(started);
                

                rx_root.findNodesByID("MySintefNode").addComponents(instance);
            }

        }
    }
    
    synchronized private void ProcessLineIfTaskType(String data) {
        if (data.startsWith("Task type=") && !data.contains("instance=")) {
            
            org.kevoree.Package rx_pkg = rx_root.findPackagesByID("sintef");

            //String componentTypeName = data.subSequence(
            //        data.indexOf("=") + 1, data.length() - 1).toString();
            String[] s1 = data.replace("Task type=", "").split(" ");
            String componentTypeName = s1[0];
            //System.err.println("Found task type <" + s1[0] + ">");
            
            //FintAndPrintTypedef("Start of ProcessLineIfTaskType()");

            org.kevoree.DeployUnit du1 = factory.createDeployUnit();
            du1.setName("sintef" + componentTypeName);
            du1.setVersion(compTypeVersionDefault);
            rx_pkg.addDeployUnits(du1);

            org.kevoree.ComponentType tf1 = factory.createComponentType();
            tf1.setName(componentTypeName);
            tf1.setVersion(compTypeVersionDefault);
            tf1.addDeployUnits(du1);

            for (int portIdx = 1; portIdx < s1.length; portIdx++) {
                String ps = s1[portIdx];
                String role = ps.substring(0, ps.indexOf("(")).toUpperCase();
                String name = ps.substring(ps.indexOf(":")+1);
                
                //System.err.println("Found port info <" + ps + ">");
                //System.err.println("Found port role <" + role + "> name <" + name + ">");                
                
                PortTypeRef porttyperef = factory.createPortTypeRef();
                porttyperef.setName(name);
                porttyperef.setOptional(false);
                if (role.contains("O")) porttyperef.setOptional(true);
                if (role.contains("P")) tf1.addProvided(porttyperef);
                if (role.contains("R")) tf1.addRequired(porttyperef);
            }

            rx_pkg.addTypeDefinitions(tf1);

            //Log.info("{} Component type= " + tf1);

        }
    }
    
    synchronized private void ProcessLineIfChannel(String data) {
        if (data.startsWith("Taskinstance=") && data.contains("port=")) {
            
            org.kevoree.Package rx_pkg = rx_root.findPackagesByID("sintef");
            
            String[] s1 = data.replace("Taskinstance=", "").replace("port=", "").replace("==>", "").split(" ");
            String tx_iid = s1[0];
            String tx_port_id = s1[1];
            String rcv_iid = s1[3];
            String rcv_port_id = s1[4];
            //Log.info("{} Got channel string<" + data + ">");
            //Log.info("{} Parsed to<" + tx_iid + "><" + tx_port_id + "><" + rcv_iid + "><" + rcv_port_id + ">");

            ComponentInstance tx_instance = rx_root.findNodesByID("MySintefNode").findComponentsByID(tx_iid);
            ComponentInstance rcv_instance = rx_root.findNodesByID("MySintefNode").findComponentsByID(rcv_iid);
            //Log.info("{} tx_comp =" + tx_instance);
            //Log.info("{} rcv_comp=" + rcv_instance);

            //FintAndPrintTypedef("Start of ProcessLineIfChannel()");
            
            if ((tx_instance != null) && (rcv_instance != null)) {
                Port tx_port  = tx_instance.findRequiredByID(tx_port_id);
                if (tx_port == null) tx_port  = tx_instance.findProvidedByID(tx_port_id);
                
                Port rcv_port = rcv_instance.findRequiredByID(rcv_port_id);
                if (rcv_port == null) rcv_port = rcv_instance.findProvidedByID(rcv_port_id);

                //Log.info("{} tx_port=" + tx_port);
                //Log.info("{} rcv_port=" + rcv_port);
                
                if ((tx_port != null) && (rcv_port != null)) {
                    // Do something
                    String channel_name = channelChecker.getChannelName(tx_iid, tx_port_id, rcv_iid, rcv_port_id);
                    //Log.info("{} Try to find a channel with name <" + channel_name + ">");

                    Channel ch_instance = rx_root.findHubsByID(channel_name);
                    if (ch_instance == null) {
                        Log.info("{} Found component and ports ... creating channel <" + channel_name + ">");
                        ch_instance = factory.createChannel();
                        ch_instance.setName(channel_name);
                        ch_instance.setTypeDefinition(rx_pkg.findTypeDefinitionsByID("sintefchannel"));

                        MBinding mb = factory.createMBinding();
                        mb.setHub(ch_instance);
                        mb.setPort(tx_port);

                        ch_instance.addBindings(mb);

                        MBinding mb2 = factory.createMBinding();
                        mb2.setHub(ch_instance);
                        mb2.setPort(rcv_port);
                        ch_instance.addBindings(mb2);

                        rx_root.addMBindings(mb);
                        rx_root.addMBindings(mb2);
                        rx_root.addHubs(ch_instance);
                    } else {
                        //Log.info("{} Found channel named <" + ch_instance.getName() + "> object:" + ch_instance);
                    }
                    // Find if channel
                } else {
                    Log.info("{} Component ports not found ... channel skipped");
                    Log.info("{} tx_port=" + tx_port);
                    Log.info("{} rcv_port=" + rcv_port);
                }
            } else {
                Log.info("{} Component instances not found ... channel skipped");
                Log.info("{} tx_instance=" + tx_instance);
                Log.info("{} rcv_instance=" + rcv_instance);
            }
        }
    }
}
