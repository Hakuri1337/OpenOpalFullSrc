package wtf.oraculus.event.impl.game.inventory;

import net.minecraft.screen.slot.SlotActionType;

public record ManualInventoryInteractionEvent(int syncId, int slotId, SlotActionType actionType) {
}
