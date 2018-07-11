package io.sece.vlc.rcvr;

import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;

import io.sece.vlc.Color;
import io.sece.vlc.Coordinate;
import io.sece.vlc.DataFrame;
import io.sece.vlc.Modem;
import io.sece.vlc.rcvr.processing.Frame;
import io.sece.vlc.rcvr.processing.FramingBlock;
import io.sece.vlc.rcvr.processing.Processing;

/**
 * Created by alex on 6/22/18.
 *
 * This class contains the basic setup of the Receiver including Modulation, FPS, Transmissionstarting
 */

public class Receiver<T extends Coordinate> {
    private Modem<Color> modem;
    private boolean transmissionStarted = false;
    private SynchronizationModule synchronizationModule;
    private int delay = 50;
    private FramingBlock framingBlock;



    public Receiver(Modem modem) {
        this.modem = modem;
        framingBlock = new FramingBlock(modem.startSequence(2), modem.bits, 800);
        Bus.subscribe(this);
    }


    public int getDelay() {
        return delay;
    }


    @Subscribe
    private void rx(Processing.Result ev) {
        Color c = ev.frame.getColorAttr(Frame.HUE);

        String currSymbol  =  modem.demodulate(c);
//        System.out.println(currSymbol + " " + ev.frame.get(Frame.IMAGE_TIMESTAMP));
        DataFrame dataFrame = (framingBlock.apply(currSymbol));
        if(dataFrame != null){

            System.out.println("Received Frame " + dataFrame.data());
        }
    }


}
