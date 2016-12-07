package org.thingml.comm.rxtx;

import jssc.SerialPort;
import jssc.SerialPortException;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
import org.kevoree.api.adaptation.AdaptationModel;
import org.kevoree.api.handler.ModelListener;
import org.kevoree.api.handler.UpdateContext;
import org.kevoree.log.Log;
import org.thingml.comm.rxtx.adaptation.AdaptationEngine;
import org.thingml.comm.rxtx.adaptation.Adaptations2Commands;
import org.thingml.comm.rxtx.serial.SerialInterpreter;
import org.thingml.comm.rxtx.serial.SerialPortReader;
import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ComponentType(version = 1)
public class SintefModComponent implements ModelListener {

    @Param(defaultValue = "/dev/ttyACM0")
    String serialportname;

    @Param(defaultValue = "100")
    Long delay;

    @Param(defaultValue = "10000")
    Long period;

    @KevoreeInject
    org.kevoree.api.Context context;

    @KevoreeInject
    ModelService modelService;

    private AdaptationEngine adaptationEngine = new AdaptationEngine();
    private Adaptations2Commands adaptations2Commands = new Adaptations2Commands();
    private ScheduledExecutorService service;
    private SerialPort serialLink;
    private AdaptationModel adaptationModel;
    private SerialInterpreter interpreter;
    private ChannelChecker channelChecker;

    @Start
    public void start() {
        Log.info("{} start() enter", context.getInstanceName());
        serialLink = new SerialPort(this.serialportname);
        channelChecker = new ChannelChecker();

        try {
            Log.info("{} port open {}", context.getInstanceName(), serialLink.openPort());// Open
            // port
            serialLink.setParams(SerialPort.BAUDRATE_115200,
                    SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS
                    + SerialPort.MASK_DSR;// Prepare mask
            serialLink.setEventsMask(mask);// Set mask
            interpreter = new SerialInterpreter(modelService, channelChecker);
            serialLink.addEventListener(new SerialPortReader(serialLink, interpreter));
        } catch (SerialPortException ex) {
            Log.info(ex.getMessage());
        }
        try {
            serialLink.writeBytes("\r\n".getBytes());
        } catch (SerialPortException e) {
            Log.error("Unable to write to serial port (reason: {})", e.getMessage());
        }
        modelService.registerModelListener(this);
        service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    String requestString = interpreter.getRequestString();
                    serialLink.writeBytes(requestString.getBytes());
                    //serialLink.writeBytes("task list \r\n".getBytes());
                    //serialLink.writeBytes("channel list \r\n".getBytes());
                } catch (SerialPortException e) {
                    Log.error("Unable to write to serial port (reason: {})", e.getMessage());
                }
            }
        }, delay, period, TimeUnit.MILLISECONDS);

        Log.info("{} start() leave", context.getInstanceName());
    }

    @Stop
    public void stop() {
        Log.info("{} stop() enter", context.getInstanceName());
        service.shutdownNow();
        try {
            serialLink.writeBytes("reset\r\n".getBytes());
            Log.info("{} Sent reset wait to get the board restarted...");
            serialLink.closePort();

            try {
                Thread.sleep(5000);                 //1000 milliseconds is one second.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            Log.info("{} wait ended", context.getInstanceName());

        } catch (SerialPortException e) {
            Log.error("Unable to write to serial port (reason: {})", e.getMessage());
        }
        modelService.unregisterModelListener(this);
        Log.info("{} stop() leave", context.getInstanceName());
    }

    public boolean afterLocalUpdate(UpdateContext arg0) {
        return true;
    }

    public boolean initUpdate(UpdateContext arg0) {
        return true;
    }

    public boolean preUpdate(UpdateContext ctx) {
        adaptationModel = adaptationEngine.diff(ctx.getCurrentModel(), ctx.getProposedModel());
        return true;
    }

    public void modelUpdated() {
        List<SerialCommand> cmds = adaptations2Commands.process(adaptationModel, channelChecker);
        Iterator<SerialCommand> it = cmds.iterator();
        SerialCommand cmd;
        while (it.hasNext()) {
            try {
                cmd = it.next();
                //Log.info("Send-to-serial: ("+cmd.priority()+")<" + cmd.toString() + ">");
                interpreter.resetInterpreter(); // Tell the interpreter.receiver that the board is reconfigured
                serialLink.writeBytes(cmd.toString().getBytes());
            } catch (SerialPortException e) {
                Log.error("Unable to write to serial port (reason: {})", e.getMessage());
            }
        }
    }

    public void postRollback(UpdateContext arg0) {
    }

    public void preRollback(UpdateContext arg0) {
    }
}
