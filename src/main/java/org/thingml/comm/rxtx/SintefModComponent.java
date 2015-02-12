package org.thingml.comm.rxtx;

import jssc.SerialPort;
import jssc.SerialPortException;
import org.kevoree.annotation.*;
import org.kevoree.api.BootstrapService;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ComponentType
public class SintefModComponent implements ModelListener {

    @Param(defaultValue = "/dev/ttyACM0")
    String serialport;

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
    private SerialPort serialPort;
    private AdaptationModel adaptationModel;

    @Start
    public void start() {
        Log.info("{} start() enter", context.getInstanceName());
        serialPort = new SerialPort(this.serialport);

        try {
            Log.info("{} port open {}", context.getInstanceName(), serialPort.openPort());// Open
            // port
            serialPort.setParams(SerialPort.BAUDRATE_115200,
                    SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS
                    + SerialPort.MASK_DSR;// Prepare mask
            serialPort.setEventsMask(mask);// Set mask
            serialPort.addEventListener(new SerialPortReader(serialPort, new SerialInterpreter()));
        } catch (SerialPortException ex) {
            Log.info(ex.getMessage());
        }
        try {
            serialPort.writeBytes("\r\n".getBytes());
        } catch (SerialPortException e) {
            Log.error("Unable to write to serial port (reason: {})", e.getMessage());
        }
        modelService.registerModelListener(this);
        service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    serialPort.writeBytes("task list \r\n".getBytes());
                    serialPort.writeBytes("channel list \r\n".getBytes());
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
            serialPort.writeBytes("reset\r\n".getBytes());
            Log.info("{} Sent reset wait to get the board restarted...");
            serialPort.closePort();

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
        Set<SerialCommand> cmds = adaptations2Commands.process(adaptationModel);
        for (SerialCommand cmd : cmds) {
            try {
                serialPort.writeBytes(cmd.toString().getBytes());
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
