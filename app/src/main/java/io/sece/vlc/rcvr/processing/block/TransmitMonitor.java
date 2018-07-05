package io.sece.vlc.rcvr.processing.block;


import java.util.concurrent.TimeUnit;

import io.sece.vlc.Color;
import io.sece.vlc.Modem;
import io.sece.vlc.rcvr.Bus;
import io.sece.vlc.rcvr.processing.Frame;
import io.sece.vlc.rcvr.processing.ProcessingBlock;
import io.sece.vlc.rcvr.utils.MovingAverage;
import io.sece.vlc.rcvr.utils.Uniq;


public class TransmitMonitor implements ProcessingBlock {
    // The window over which to calculate the moving average FPS
    private static final long AVG_WINDOW = 300;
    private static final TimeUnit AVG_WINDOW_UNIT = TimeUnit.MILLISECONDS;

    private double fpsExpected;

    private MovingAverage fpsReceived = new MovingAverage(AVG_WINDOW, AVG_WINDOW_UNIT);
    private Uniq uniq = new Uniq();

    private Modem<Color> modem;

    private long previousTimestamp = -1;
    private Color prevColor;
    private int offset;

    public static class Event extends Bus.Event {
        public boolean transmissionInProgress = false;
        public double fps;

        public Event(double fps, boolean transmissionInProgress) {
            this.fps = fps;
            this.transmissionInProgress = transmissionInProgress;
        }
    }


    public TransmitMonitor(double fpsExpected, Modem modem, int offset) {
        this.fpsExpected = fpsExpected;
        this.modem = modem;
        this.offset = offset;
    }


    public synchronized Frame apply(Frame frame) {

        Color currColor =  modem.detect(new Color(((int)(long)frame.get(Frame.HUE)), (int)(long)frame.get(Frame.BRIGHTNESS)));

        if ( prevColor != null) {

            // detect Color Changes
            if(!prevColor.equals(currColor)){

                long timestamp = frame.get(Frame.IMAGE_TIMESTAMP);

                // Calculate the FPS moving average value
                fpsReceived.update(1.0e9d / (double)(timestamp - previousTimestamp));

                // Round the average value to one decimal point to make sure we don't send updates
                // too often
                double calcFPS = Math.round(10.0d * fpsReceived.value) / 10.0d;

                if (uniq.hasChanged(calcFPS)){
                    Bus.send(new Event(calcFPS, (calcFPS > (fpsExpected - offset) && calcFPS < (fpsExpected + offset))));
                }
                previousTimestamp = timestamp;
            }
        }
        prevColor = currColor;
        return frame;
    }
}