package wtf.oraculus.duck;

import net.minecraft.util.Hand;

public interface ClientPlayerEntityAccess {
    void oraculus$swingHandClientside(Hand hand);

    void oraculus$swingHandServerside(Hand hand);
}
