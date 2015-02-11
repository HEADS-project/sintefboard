package org.thingml.comm.rxtx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jssc.SerialPort;
import jssc.SerialPortException;

import org.kevoree.Channel;
import org.kevoree.ChannelType;
import org.kevoree.ComponentInstance;
import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Instance;
import org.kevoree.MBinding;
import org.kevoree.Port;
import org.kevoree.PortTypeRef;
import org.kevoree.TypeDefinition;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.annotation.Update;
import org.kevoree.api.BootstrapService;
import org.kevoree.api.ModelService;
import org.kevoree.api.adaptation.AdaptationModel;
import org.kevoree.api.adaptation.AdaptationPrimitive;
import org.kevoree.api.adaptation.AdaptationType;
import org.kevoree.api.handler.ModelListener;
import org.kevoree.api.handler.UpdateCallback;
import org.kevoree.api.handler.UpdateContext;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.KMFContainer;
import org.kevoree.pmodeling.api.ModelCloner;
import org.kevoree.pmodeling.api.compare.ModelCompare;
import org.kevoree.pmodeling.api.trace.ModelAddTrace;
import org.kevoree.pmodeling.api.trace.ModelRemoveTrace;
import org.kevoree.pmodeling.api.trace.ModelSetTrace;
import org.kevoree.pmodeling.api.trace.ModelTrace;
import org.kevoree.pmodeling.api.trace.TraceSequence;

@ComponentType
public class SintefModComponent implements ModelListener, Runnable,
        SerialObserver {

    @Param(defaultValue = "/dev/ttyACM0")
    String serialport;

    @Param(defaultValue = "100")
    Long delay;

    @Param(defaultValue = "10000")
    Long period;

    ScheduledExecutorService service = null;

    @KevoreeInject
    org.kevoree.api.Context context;

    @KevoreeInject
    ModelService modelService;

    @KevoreeInject
    BootstrapService bootstrap;

    // @Output
    // org.kevoree.api.Port out;

    SerialPort serialPort;

    @Start
    public void start() {
        System.err.println("SintefModComponent::start() enter");
        serialPort = new SerialPort(this.serialport);

        try {
            System.out.println("port open :" + serialPort.openPort());// Open
            // port
            serialPort.setParams(SerialPort.BAUDRATE_115200,
                    SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS
                    + SerialPort.MASK_DSR;// Prepare mask
            serialPort.setEventsMask(mask);// Set mask
            serialPort.addEventListener(new SerialPortReader(serialPort, this));
        } catch (SerialPortException ex) {
            System.out.println(ex);
        }
        try {
            serialPort.writeBytes("\r\n".getBytes());
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        modelService.registerModelListener(this);
        service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(this, delay, period, TimeUnit.MILLISECONDS);

        System.err.println("SintefModComponent::start() leave");
    }

    @Stop
    public void stop() {
        System.err.println("SintefModComponent::stop() enter");
        service.shutdownNow();
        try {
            serialPort.writeBytes("reset\r\n".getBytes());
            System.err.println("Sent reset wait to get the board restarted...");
            serialPort.closePort();

            try {
                Thread.sleep(5000);                 //1000 milliseconds is one second.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            System.err.println("wait ended");

        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        modelService.unregisterModelListener(this);
        System.err.println("SintefModComponent::stop() leave");
    }

    @Update
    public void update() {
        //this.stop();
        //this.start();
    }

    public boolean afterLocalUpdate(UpdateContext arg0) {
        return true;
    }

    public boolean initUpdate(UpdateContext arg0) {
        return true;
    }

    /* Helper to create command */
    private AdaptationPrimitive adapt(AdaptationType primitive, Object elem) {
        System.err.println("SintefModComponent::adapt() enter");

        AdaptationPrimitive ccmd = new AdaptationPrimitive();
        ccmd.setPrimitiveType(primitive.name());
        ccmd.setRef(elem);

        System.err.println("SintefModComponent::adapt() leave");
        return ccmd;
    }

    public boolean preUpdate(UpdateContext ctx) {
        System.err.println("SintefModComponent::preUpdate() enter");

        TraceSequence s = comp.diff(ctx.getCurrentModel(),
                ctx.getProposedModel());
        HashMap<String, TupleObjPrim> elementAlreadyProcessed = new HashMap<String, TupleObjPrim>();

        adaptationModel = new AdaptationModel();
        ContainerNode targetNode = ctx.getProposedModel().findNodesByID(
                "MySintefNode");

        ContainerRoot targetModel = ctx.getProposedModel();
        ContainerRoot currentModel = ctx.getCurrentModel();
        for (ModelTrace trace : s.getTraces()) {
            KMFContainer modelElement = targetModel.findByPath(trace
                    .getSrcPath());
            if (trace.getRefName().equals("components")) {
                if (trace.getSrcPath().equals(targetNode.path())) {
                    if (trace instanceof ModelAddTrace) {
                        KMFContainer elemToAdd = ctx.getProposedModel()
                                .findByPath(
                                        ((ModelAddTrace) trace)
                                                .getPreviousPath());
                        adaptationModel.getAdaptations().add(
                                adapt(AdaptationType.AddInstance, elemToAdd));
                    }
                    if (trace instanceof ModelRemoveTrace) {
                        KMFContainer elemToAdd = ctx
                                .getCurrentModel()
                                .findByPath(
                                        ((ModelRemoveTrace) trace).getObjPath());
                        adaptationModel.getAdaptations()
                                .add(adapt(AdaptationType.RemoveInstance,
                                        elemToAdd));
                        adaptationModel.getAdaptations().add(
                                adapt(AdaptationType.StopInstance, elemToAdd));
                    }
                }
            }
            if (trace.getRefName().equals("bindings")) {
                if (!(targetModel.findByPath(trace.getSrcPath()) instanceof Channel)) {
                    if (trace instanceof ModelAddTrace) {
                        MBinding binding = (MBinding) targetModel
                                .findByPath(((ModelAddTrace) trace)
                                        .getPreviousPath());
                        adaptationModel.getAdaptations().add(
                                adapt(AdaptationType.AddBinding, binding));
                        Channel channel = binding.getHub();
                        if (channel != null) {
                            TupleObjPrim newTuple = new TupleObjPrim(channel,
                                    AdaptationType.AddInstance);
                            if (!elementAlreadyProcessed.containsKey(newTuple
                                    .getKey())) {
                                adaptationModel.getAdaptations().add(
                                        adapt(AdaptationType.AddInstance,
                                                channel));
                                elementAlreadyProcessed.put(newTuple.getKey(),
                                        newTuple);
                            }
                        }
                    }
                    if (trace instanceof ModelRemoveTrace) {
                        org.kevoree.MBinding binding = (org.kevoree.MBinding) currentModel
                                .findByPath(((ModelRemoveTrace) trace)
                                        .getObjPath());
                        org.kevoree.MBinding previousBinding = (org.kevoree.MBinding) currentModel
                                .findByPath(((ModelRemoveTrace) trace)
                                        .getObjPath());
                        Channel channel = binding.getHub();
                        Channel oldChannel = previousBinding.getHub();
                        // check if not no current usage of this channel
                        boolean stillUsed = channel != null;
                        if (channel != null) {
                            for (MBinding loopBinding : channel.getBindings()) {
                                if (loopBinding.getPort() != null) {
                                    if (loopBinding.getPort().eContainer()
                                            .equals(targetNode)) {
                                        stillUsed = true;
                                    }
                                }
                            }
                        }
                        if (!stillUsed) {
                            TupleObjPrim removeTuple = new TupleObjPrim(
                                    oldChannel, AdaptationType.RemoveInstance);
                            if (!elementAlreadyProcessed
                                    .containsKey(removeTuple.getKey())) {
                                adaptationModel.getAdaptations().add(
                                        adapt(AdaptationType.RemoveInstance,
                                                oldChannel));
                                elementAlreadyProcessed.put(
                                        removeTuple.getKey(), removeTuple);
                                TupleObjPrim stopTuple = new TupleObjPrim(
                                        oldChannel, AdaptationType.StopInstance);
                                elementAlreadyProcessed.put(stopTuple.getKey(),
                                        stopTuple);
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
                            if (((ModelSetTrace) trace).getContent()
                                    .toLowerCase().equals("true")) {
                                TupleObjPrim sIT = new TupleObjPrim(
                                        modelElement,
                                        AdaptationType.StartInstance);
                                if (!elementAlreadyProcessed.containsKey(sIT
                                        .getKey())) {
                                    adaptationModel.getAdaptations().add(
                                            adapt(AdaptationType.StartInstance,
                                                    modelElement));
                                    elementAlreadyProcessed.put(sIT.getKey(),
                                            sIT);
                                }
                            } else {
                                TupleObjPrim sit = new TupleObjPrim(
                                        modelElement,
                                        AdaptationType.StopInstance);
                                if (!elementAlreadyProcessed.containsKey(sit
                                        .getKey())) {
                                    adaptationModel.getAdaptations().add(
                                            adapt(AdaptationType.StopInstance,
                                                    modelElement));
                                    elementAlreadyProcessed.put(sit.getKey(),
                                            sit);
                                }
                            }
                        }
                    }
                }
            }

        }
        System.err.println("SintefModComponent::preUpdate() leave");
        return true;
    }

    boolean mustRest = false;

    public void modelUpdated() {
        int loopCount = -1;
        System.err.println("SintefModComponent::modelUpdated() enter");

        for (AdaptationPrimitive p : adaptationModel.getAdaptations()) {
            loopCount++;
            if (p.getPrimitiveType().equals(AdaptationType.AddInstance.name())) {
                System.err.println("If AddInstance..." + loopCount);
                try {
                    System.err.println("task instantiate "
                            + ((ComponentInstance) p.getRef())
                            .getTypeDefinition().getName()
                            + " "
                            + ((ComponentInstance) p.getRef())
                            .getName() + "\r\n");
                    serialPort
                            .writeBytes(("task instantiate "
                                    + ((ComponentInstance) p.getRef())
                                    .getTypeDefinition().getName()
                                    + " "
                                    + ((ComponentInstance) p.getRef())
                                    .getName() + "\r\n").getBytes());
                } catch (SerialPortException e) {

                    e.printStackTrace();
                }
                System.err.println("...done" + loopCount);
            } else if (p.getPrimitiveType().equals(
                    AdaptationType.RemoveInstance.name())) {
                System.err.println("If RemoveInstance..." + loopCount);
                // TODO
                System.err.println("cannot remove instance");
                System.err.println("...done" + loopCount);
            } else if (p.getPrimitiveType().equals(
                    AdaptationType.AddBinding.name())) {
                System.err.println("If AddBinding..." + loopCount);
                Channel arg0 = ((MBinding) p.getRef()).getHub();
                if (((ComponentInstance) ((Channel) arg0).getBindings().get(0)
                        .getPort().eContainer()).getTypeDefinition()
                        .getDeployUnits().get(0).getName().startsWith("sintef")
                        && ((ComponentInstance) ((Channel) arg0).getBindings()
                        .get(1).getPort().eContainer())
                        .getTypeDefinition().getDeployUnits().get(0)
                        .getName().startsWith("sintef")
                        && ((Channel) arg0).getBindings().size() == 2) {
                    try {
                        System.err.println("channel create "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(0).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(0)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(1).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(1)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " " + "\r\n");

                        serialPort.writeBytes(("channel create "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(0).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(0)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(1).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(1)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " " + "\r\n").getBytes());
                    } catch (SerialPortException e) {

                        e.printStackTrace();
                    }
                }
                System.err.println("...done" + loopCount);
            } else if (p.getPrimitiveType().equals(
                    AdaptationType.RemoveBinding.name())) {
                System.err.println("If Remove Binding..." + loopCount);
                Channel arg0 = ((MBinding) p.getRef()).getHub();
                if (((ComponentInstance) ((Channel) arg0).getBindings().get(0)
                        .getPort().eContainer()).getTypeDefinition()
                        .getDeployUnits().get(0).getName().startsWith("sintef")
                        && ((ComponentInstance) ((Channel) arg0).getBindings()
                        .get(1).getPort().eContainer())
                        .getTypeDefinition().getDeployUnits().get(0)
                        .getName().startsWith("sintef")
                        && ((Channel) arg0).getBindings().size() == 2) {
                    try {
                        System.err.println("channel delete "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(0).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(0)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(1).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(1)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " " + "\r\n");
                        serialPort.writeBytes(("channel delete "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(0).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(0)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " "
                                + ((ComponentInstance) ((Channel) arg0)
                                .getBindings().get(1).getPort()
                                .eContainer()).getName()
                                + " "
                                + ((Channel) arg0).getBindings().get(1)
                                .getPort().getPortTypeRef().getName()
                                .replace("tx", "").replace("rcv", "")
                                + " " + "\r\n").getBytes());
                    } catch (SerialPortException e) {

                        e.printStackTrace();
                    }
                }
                System.err.println("...done" + loopCount);
            }
        }

        System.err.println("SintefModComponent::modelUpdated() 2");

        try {
            if (adaptationModel.getAdaptations().size() > 0) {
                System.err.println(adaptationModel.getAdaptations());
                for (AdaptationPrimitive p1 : adaptationModel.getAdaptations())
                    System.err.println(p1.getPrimitiveType() + " " + p1.getRef());


                System.err.println("task create\r\n");
                serialPort.writeBytes("task create\r\n".getBytes());
            }
        } catch (SerialPortException e) {

            e.printStackTrace();
        }

        System.err.println("SintefModComponent::modelUpdated() 3");

        for (AdaptationPrimitive p : adaptationModel.getAdaptations()) {
            if (p.getPrimitiveType()
                    .equals(AdaptationType.StartInstance.name())) {
                try {
                    System.err.println("task resume "
                            + ((ComponentInstance) p.getRef())
                            .getName() + "\r\n");
                    serialPort
                            .writeBytes(("task resume "
                                    + ((ComponentInstance) p.getRef())
                                    .getName() + "\r\n").getBytes());
                } catch (SerialPortException e) {

                    e.printStackTrace();
                }
            } else if (p.getPrimitiveType().equals(
                    AdaptationType.StopInstance.name())) {
                try {
                    System.err.println("task suspend "
                            + ((ComponentInstance) p.getRef())
                            .getName() + "\r\n");
                    serialPort
                            .writeBytes(("task suspend "
                                    + ((ComponentInstance) p.getRef())
                                    .getName() + "\r\n").getBytes());
                } catch (SerialPortException e) {

                    e.printStackTrace();
                }
            }
        }

        System.err.println("SintefModComponent::modelUpdated() leave");

    }

    public void postRollback(UpdateContext arg0) {
    }

    public void preRollback(UpdateContext arg0) {
    }

    public void run() {
        try {
            serialPort.writeBytes("task list \r\n".getBytes());
            serialPort.writeBytes("channel list \r\n".getBytes());
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

    }

    private KevoreeFactory factory = new DefaultKevoreeFactory();
    ModelCompare comp = factory.createModelCompare();
    ModelCloner cloner = factory.createModelCloner();

    private org.kevoree.Package p;

    private ContainerRoot r;

    private AdaptationModel adaptationModel;

    public void receive(String data) {
        System.err.println("PROMPT " + data);
        if (!data.startsWith("HEADS->norm>")) {

            if (data.equals("Listing of supported task types")) {
                p = factory.createPackage();
                p.setName("sintef");

                org.kevoree.DeployUnit du = factory.createDeployUnit();
                du.setName("sintefnodetype");
                du.setVersion("1.0.0");
                p.addDeployUnits(du);

                TypeDefinition tf = factory.createNodeType();
                tf.setName("sintefnodetype");
                tf.setVersion("1.0.0");
                tf.addDeployUnits(du);

                p.addTypeDefinitions(tf);

                org.kevoree.DeployUnit du1 = factory.createDeployUnit();
                du1.setName("sintefchannel");
                du1.setVersion("1.0.0");
                p.addDeployUnits(du1);

                ChannelType tf1 = factory.createChannelType();
                tf1.setName("sintefchannel");
                tf1.setVersion("1.0.0");
                tf1.addDeployUnits(du1);

                p.addTypeDefinitions(tf);
                p.addTypeDefinitions(tf1);

                ContainerNode n = factory.createContainerNode();
                n.setStarted(true);
                n.setName("MySintefNode");
                n.setTypeDefinition(tf);
                r = cloner.clone(modelService.getCurrentModel().getModel());
                factory.root(r);

                r.addPackages(p);
                r.addNodes(n);
                n.setGroups(r.getGroups());

            }

            if (data.equals("Listing of channels connected to current class instances")) {
                // TODO
            }
            //Task type=sender instance=tx state=STARTED
            //Task type=rxlcd instance=rcv state=STARTED
            //Taskinstance=tx port=0 ==> Taskinstance=rcv port=0
            //Taskinstance=rcv port=0 ==> Taskinstance=tx port=0

            if (data.startsWith("Taskinstance=") && data.contains("port=")) {
                String[] s1 = data.replace("Taskinstance=", "").replace("port=", "").replace("==>", "").split(" ");
                String tx_iid = s1[0];
                String tx_port_id = "tx" + s1[1];
                String rcv_iid = s1[3];
                String rcv_port_id = "rcv" + s1[4];
                System.err.println("Got channel string<" + data + ">");
                System.err.println("Parsed to<" + tx_iid + "><" + tx_port_id + "><" + rcv_iid + "><" + rcv_port_id + ">");

                ComponentInstance tx_instance = r.findNodesByID("MySintefNode").findComponentsByID(tx_iid);
                ComponentInstance rcv_instance = r.findNodesByID("MySintefNode").findComponentsByID(rcv_iid);
                if ((tx_instance != null) && (rcv_instance != null)) {
                    //PortTypeRef tx_port_ref = null;
                    Port tx_port = tx_instance.findRequiredByID(tx_port_id);
                    //if (tx_port != null) tx_port_ref = tx_port.getPortTypeRef();

                    //PortTypeRef rcv_port_ref = null;
                    Port rcv_port = rcv_instance.findProvidedByID(rcv_port_id);
                    //if (rcv_port != null) rcv_port_ref = rcv_port.getPortTypeRef();

                    if ((tx_port != null) && (rcv_port != null)) {
                        // Do something
                        String channel_name = "" + tx_iid + "_" + tx_port_id + "_" + rcv_iid + "_" + rcv_port_id;  // TODO  make this convention more robust
                        System.err.println("Try to find a channel with name <" + channel_name + ">");

                        Channel ch_instance = r.findHubsByID(channel_name);
                        if (ch_instance == null) {
                            System.err.println("Found component and ports ... creating channel <" + channel_name + ">");
                            ch_instance = factory.createChannel();
                            ch_instance.setName(channel_name);
                            ch_instance.setTypeDefinition(p.findTypeDefinitionsByID("sintefchannel"));

                            MBinding mb = factory.createMBinding();
                            mb.setHub(ch_instance);
                            mb.setPort(tx_port);

                            ch_instance.addBindings(mb);

                            MBinding mb2 = factory.createMBinding();
                            mb2.setHub(ch_instance);
                            mb2.setPort(rcv_port);
                            ch_instance.addBindings(mb2);

                            r.addMBindings(mb);
                            r.addMBindings(mb2);
                            r.addHubs(ch_instance);
                        } else {
                            System.err.println("Found channel named <" + ch_instance.getName() + "> object:" + ch_instance);
                        }
                        // Find if channel
                    } else {
                        System.err.println("Component ports not found ... channel skipped");
                        System.err.println("tx_port=" + tx_port);
                        System.err.println("rcv_port=" + rcv_port);
                    }
                } else {
                    System.err.println("Component instances not found ... channel skipped");
                    System.err.println("tx_instance=" + tx_instance);
                    System.err.println("rcv_instance=" + rcv_instance);
                }
            }

            if (data.startsWith("Task type=") && data.contains("instance=")) {
                String[] s1 = data.replace("Task type=", "").replace("instance=", "").replace("state=", "").split(" ");
                String tid = s1[0];
                String iid = s1[1];
                boolean started = s1[2].equals("STARTED");
                TypeDefinition t = r.findPackagesByID("sintef").findTypeDefinitionsByID(tid);
                ComponentInstance instance = r.findNodesByID("MySintefNode").findComponentsByID(iid);

                if (t == null) {
                    t = factory.createTypeDefinition();
                    t.setName(tid);

                    r.findPackagesByID("sintef").addTypeDefinitions(t);
                }
                if (instance == null) {
                    instance = factory.createComponentInstance();
                    instance.setTypeDefinition(t);
                    instance.setName(iid);

                    r.findNodesByID("MySintefNode").addComponents(instance);
                }

                System.err.println("Type def=" + t);
                System.err.println("Type inst=" + instance);

            }

            if (data.startsWith("Task type=") && !data.contains("instance=")) {
                String componentTypeName = data.subSequence(
                        data.indexOf("=") + 1, data.length() - 1).toString();
                org.kevoree.DeployUnit du1 = factory.createDeployUnit();
                du1.setName("sintef" + componentTypeName);
                du1.setVersion("1.0.0");
                p.addDeployUnits(du1);

                org.kevoree.ComponentType tf1 = factory.createComponentType();
                tf1.setName(componentTypeName);
                tf1.setVersion("1.0.0");
                tf1.addDeployUnits(du1);

                for (int j = 0; j < 6; j++) {
                    PortTypeRef porttyperef = factory.createPortTypeRef();
                    porttyperef.setName("rcv" + j);
                    porttyperef.setOptional(true);

                    tf1.addProvided(porttyperef);

                    PortTypeRef porttyperef1 = factory.createPortTypeRef();
                    porttyperef1.setName("tx" + j);
                    porttyperef1.setOptional(true);
                    tf1.addRequired(porttyperef1);
                }

                p.addTypeDefinitions(tf1);

                System.err.println("Component type= " + tf1);
            }
            if (data.equals("End of listing") && r != null) {
                modelService.update(r, new UpdateCallback() {
                    public void run(Boolean applied) {

                    }
                });
            }

        }

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
