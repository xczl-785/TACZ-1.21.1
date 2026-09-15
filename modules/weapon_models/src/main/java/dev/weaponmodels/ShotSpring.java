package dev.weaponmodels;

/** Exact critically damped recovery. Shots add velocity; they never reset position. */
public final class ShotSpring {
    private final double frequency, limit;
    private double position, velocity;
    public ShotSpring(double frequency,double limit){
        if(!Double.isFinite(frequency+limit)||frequency<=0||limit<=0)throw new IllegalArgumentException("Invalid spring");
        this.frequency=frequency;this.limit=limit;
    }
    public void kick(double amount){
        if(!Double.isFinite(amount))throw new IllegalArgumentException("Non-finite shot");
        velocity=Math.clamp(velocity+amount*frequency, -limit*frequency,limit*frequency);
    }
    public void advance(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid time delta");
        double c=velocity+frequency*position, decay=Math.exp(-frequency*seconds);
        position=Math.clamp((position+c*seconds)*decay,-limit,limit);
        velocity=(velocity-frequency*c*seconds)*decay;
    }
    public double value(){return position;}
    public void reset(){position=0;velocity=0;}
}
