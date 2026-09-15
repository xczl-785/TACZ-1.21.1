package dev.weaponmodels;

/** One normalization for both camera and visual response; the reference never changes with attachments. */
public final class RecoilResponse {
    public static float factor(double base,double attachmentFraction,double reference,double tuning) {
        if(!Double.isFinite(base)||!Double.isFinite(attachmentFraction)||!Double.isFinite(reference)||!Double.isFinite(tuning)
                ||base<0||attachmentFraction < -1||reference<=0||tuning<0)throw new IllegalArgumentException("Invalid recoil response");
        float result=(float)(base*(1+attachmentFraction)/reference*tuning);
        if(!Float.isFinite(result))throw new IllegalArgumentException("Recoil response overflow");
        return result;
    }
    private RecoilResponse(){}
}
