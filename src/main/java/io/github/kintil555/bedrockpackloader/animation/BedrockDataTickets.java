package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.constant.dataticket.DataTicket;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;

/** GeckoLib render-state data used to hand Bedrock context from extraction to animation. */
public final class BedrockDataTickets {
    /** Set on a render state that the Bedrock renderer took over. */
    public static final DataTicket<Boolean> ACTIVE = DataTicket.create("bedrockpackloader_active", Boolean.class);
    /** The entity values of the frame, captured when the render state is extracted. */
    public static final DataTicket<EntityQuerySnapshot> SNAPSHOT = DataTicket.create("bedrockpackloader_snapshot", EntityQuerySnapshot.class);
    /** The Molang scope of the animated entity, read by {@code MolangVariableBridge} while sampling keyframes. */
    public static final DataTicket<MolangScope> SCOPE = DataTicket.create("bedrockpackloader_scope", MolangScope.class);

    private BedrockDataTickets() {
    }
}
