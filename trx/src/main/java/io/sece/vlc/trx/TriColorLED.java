package io.sece.vlc.trx;

import io.sece.pigpio.PiGPIO;

class TriColorLED implements LEDInterface
{
    int redPin;
    int greenPin;
    int bluePin;
    public TriColorLED(int redPin, int greenPin, int bluePin)
    {
        //GPIO pins which are connected to the specific pins of the LED
        this.redPin = redPin;
        this.greenPin = greenPin;
        this.bluePin = bluePin;
    }
    public void setIntensity(boolean onoff)
    {
            throw new UnsupportedOperationException();
    }

    public void setIntensity(int value)
    {
            throw new UnsupportedOperationException();
    }

    public void setColor(int red, int green, int blue)
    {
        try
        {
                PiGPIO.gpioPWM(redPin, red);
                PiGPIO.gpioPWM(greenPin, green);
                PiGPIO.gpioPWM(bluePin, blue);
        }
        catch(Exception e)
        {
                System.out.println("error in setColor: " + e);
        }
    }
}
