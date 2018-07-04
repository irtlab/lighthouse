package io.sece.vlc;



public class ASK2Modulator extends AmpModulator {
    private Amplitude l1, l2;
    private Symbol symbol;

    public ASK2Modulator() {
        this(0, 255);
    }

    public ASK2Modulator(int l1, int l2) {
        this.l1 = new Amplitude(l1);
        this.l2 = new Amplitude(l2);
        states = 2;
        symbol = new Symbol(states);
        bits = symbol.bits;
    }

    @Override
    public Amplitude detect(Amplitude input) {
        return nearestNeighbor(input, l1, l2);
    }

    @Override
    public Amplitude modulate(String data, int offset) {
        switch (symbol.fromBits(data, offset)) {
            case 0:
                return l1;
            case 1:
                return l2;
        }
        throw new AssertionError();
    }

    @Override
    public StringBuilder demodulate(StringBuilder buf, int offset, Amplitude input) {
        throw new UnsupportedOperationException();
    }
}
