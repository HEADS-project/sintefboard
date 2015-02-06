package org.thingml.comm.rxtx;

import java.util.HashMap;
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

	}

	@Stop
	public void stop() {
		service.shutdownNow();
		try {
			serialPort.writeBytes("reset\r\n".getBytes());
			System.err.println("Sent reset wait to get the board restarted...");
			serialPort.closePort();
			
			try {
				Thread.sleep(5000);                 //1000 milliseconds is one second.
			} catch(InterruptedException ex) {
				Thread.currentThread().interrupt();
			}			
			System.err.println("wait ended");
			
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
		modelService.unregisterModelListener(this);
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
		AdaptationPrimitive ccmd = new AdaptationPrimitive();
		ccmd.setPrimitiveType(primitive.name());
		ccmd.setRef(elem);
		return ccmd;
	}

	public boolean preUpdate(UpdateContext ctx) {
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
		return true;
	}

	boolean mustRest = false;

	public void modelUpdated() {

		for (AdaptationPrimitive p : adaptationModel.getAdaptations()) {
			if (p.getPrimitiveType().equals(AdaptationType.AddInstance.name())) {
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
			} else if (p.getPrimitiveType().equals(
					AdaptationType.RemoveInstance.name())) {
				// TODO
				System.err.println("cannot remove instance");
			} else if (p.getPrimitiveType().equals(
					AdaptationType.AddBinding.name())) {
				System.err.println("add binding");
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
			}

			else if (p.getPrimitiveType().equals(
					AdaptationType.RemoveBinding.name())) {
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
			}
		}
		
		try {
			if (adaptationModel.getAdaptations().size()>0 ){
				System.err.println(adaptationModel.getAdaptations());
				for (AdaptationPrimitive p1 : adaptationModel.getAdaptations())
					System.err.println(p1.getPrimitiveType() + " " + p1.getRef());
			serialPort.writeBytes("task create\r\n".getBytes());
			}
		} catch (SerialPortException e) {

			e.printStackTrace();
		}

		
		for (AdaptationPrimitive p : adaptationModel.getAdaptations()) {
			if (p.getPrimitiveType()
					.equals(AdaptationType.StartInstance.name())) {
				try {
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
					serialPort
							.writeBytes(("task suspend "
									+ ((ComponentInstance) p.getRef())
											.getName() + "\r\n").getBytes());
				} catch (SerialPortException e) {

					e.printStackTrace();
				}
			}
		}
	

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
				;

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

			if (data.startsWith("Task type=") && data.contains("instance=")) {
				String[] s1 = data.replace("Task type=", "").replace("instance=", "").replace("state=", "").split(" ");				
				String tid =s1[0];
				String iid=s1[1];
				boolean started =s1[2].equals("STARTED");							
				TypeDefinition t  =r.findPackagesByID("sintef").findTypeDefinitionsByID(tid);
				ComponentInstance instance = r.findNodesByID("MySintefNode").findComponentsByID(iid);
				
				System.err.println(t);
				System.err.println(instance);

				
				
				
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
